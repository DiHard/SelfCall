import asyncio
import logging
import secrets
import sqlite3
import time
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials
from pydantic import BaseModel, Field

from .android_updates import require_supported
from .config import settings
from .rate_limit import RateLimiter

log = logging.getLogger("selfcall")
router = APIRouter(prefix="/api/calls")
RING_SECONDS = 35
CONNECT_SECONDS = 90
CallStatus = Literal["ringing", "accepted", "declined", "cancelled", "expired", "unreachable", "ended"]


def error(status: int, code: str, message: str) -> HTTPException:
    return HTTPException(status, detail={"code": code, "message": message})


class Registration(BaseModel):
    device_id: UUID
    name: str = Field(min_length=1, max_length=80)
    token: str = Field(min_length=16, max_length=4096)


class Device(BaseModel):
    device_id: UUID


class Refresh(Device):
    token: str = Field(min_length=16, max_length=4096)


class Dial(BaseModel):
    id: UUID
    login: str = Field(min_length=1, max_length=128)


class Contact(BaseModel):
    login: str
    name: str


class Directory(BaseModel):
    incoming: Contact | None
    contacts: list[Contact]


class CallView(BaseModel):
    id: UUID
    name: str
    status: CallStatus
    incoming: bool
    remaining_ms: int


@dataclass
class Call:
    id: UUID
    caller: Contact
    callee: Contact
    deadline: float
    status: CallStatus = "ringing"
    room_created: bool = False

    @property
    def room(self) -> str:
        return f"call-{self.id.hex}"

    def view(self, login: str) -> CallView:
        incoming = login == self.callee.login
        return CallView(id=self.id, name=(self.caller if incoming else self.callee).name,
                        status=self.status, incoming=incoming,
                        remaining_ms=max(0, int((self.deadline - time.time()) * 1000)))


class Firebase:
    def __init__(self, credentials_path: str):
        self.credentials = Credentials.from_service_account_file(
            credentials_path, scopes=["https://www.googleapis.com/auth/firebase.messaging"],
        )
        self.session = AuthorizedSession(self.credentials, refresh_timeout=7)
        self.url = f"https://fcm.googleapis.com/v1/projects/{self.credentials.project_id}/messages:send"
        self.lock = asyncio.Lock()

    def close(self) -> None:
        self.session.close()

    def _send(self, token: str, data: dict[str, str], ttl: int) -> str:
        priority = "HIGH" if data["type"] == "incoming_call" else "NORMAL"
        with self.session.post(self.url, json={"message": {
            "token": token,
            "data": data,
            "android": {"priority": priority, "ttl": f"{ttl}s",
                        "restricted_package_name": "ru.ifx.selfcall"},
        }}, timeout=7) as response:
            if response.ok:
                return "sent"
            details = response.json().get("error", {}).get("details", [])
            if any(isinstance(item, dict) and item.get("errorCode") == "UNREGISTERED" for item in details):
                return "unregistered"
            log.warning("FCM rejected a call notification (HTTP %s)", response.status_code)
            return "failed"

    async def send(self, token: str, data: dict[str, str], ttl: int) -> str:
        async with self.lock:
            try:
                return await asyncio.to_thread(self._send, token, data, ttl)
            except Exception as exc:
                log.warning("Could not send call notification (%s)", type(exc).__name__)
                return "failed"


class DirectCalls:
    def __init__(self, database: str, push: Firebase):
        self.push = push
        self.database = database
        Path(database).parent.mkdir(parents=True, exist_ok=True)
        with closing(self.connect()) as db, db:
            db.execute("""CREATE TABLE IF NOT EXISTS devices (
                login TEXT PRIMARY KEY, name TEXT NOT NULL,
                device_id TEXT NOT NULL UNIQUE, token TEXT NOT NULL UNIQUE
            )""")
        self.calls: dict[UUID, Call] = {}
        self.lock = asyncio.Lock()
        self.dial_limit = RateLimiter(8, 60)

    def connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.database)
        db.row_factory = sqlite3.Row
        return db

    def get_device(self, login: str):
        with closing(self.connect()) as db:
            return db.execute("SELECT * FROM devices WHERE login = ?", (login,)).fetchone()

    def cleanup(self) -> None:
        now = time.time()
        for call_id, call in list(self.calls.items()):
            if call.deadline + settings.max_meeting_hours * 3600 + 60 < now:
                del self.calls[call_id]
            elif call.status == "ringing" and call.deadline <= now:
                call.status = "expired"

    def find(self, call_id: UUID, login: str) -> Call:
        self.cleanup()
        call = self.calls.get(call_id)
        if not call or login not in (call.caller.login, call.callee.login):
            raise error(404, "call_not_found", "Вызов не найден")
        return call

    async def close_room(self, call: Call, request: Request) -> None:
        if call.room_created:
            try:
                await request.app.state.rooms.delete_room(call.room)
            except Exception as exc:
                log.warning("Could not close a direct call room (%s)", type(exc).__name__)


def access(request: Request) -> tuple[DirectCalls, str]:
    calls = getattr(request.app.state, "direct_calls", None)
    if calls is None:
        raise error(503, "calls_unavailable", "Прямые звонки пока недоступны")
    # The host nginx overwrites this header with the result of Basic authentication.
    login = request.headers.get("x-selfcall-login", "").strip()
    secret = request.headers.get("x-selfcall-proxy-secret", "")
    if not login or len(login) > 128 or not secrets.compare_digest(
        secret.encode(), settings.direct_calls_proxy_secret.encode(),
    ):
        raise error(401, "sign_in_required", "Войдите в SelfCall")
    # Browsers can send cached Basic credentials with cross-origin forms.
    if request.method == "POST" and request.headers.get("sec-fetch-site") in ("cross-site", "same-site"):
        raise error(403, "call_action_forbidden", "Откройте SelfCall, чтобы выполнить это действие")
    return calls, login


@router.get("/directory", response_model=Directory)
def directory(request: Request, device_id: UUID | None = None) -> Directory:
    calls, login = access(request)
    require_supported(request, android_only=device_id is not None)
    with closing(calls.connect()) as db:
        contacts = [Contact(login=row["login"], name=row["name"])
                    for row in db.execute("SELECT login, name FROM devices WHERE login != ?", (login,))]
        own = db.execute("SELECT name FROM devices WHERE login = ? AND device_id = ?",
                         (login, str(device_id))).fetchone() if device_id is not None else None
    incoming = Contact(login=login, name=own["name"]) if own else None
    return Directory(incoming=incoming, contacts=sorted(
        contacts,
        key=lambda contact: (contact.name.casefold(), contact.login),
    ))


@router.post("/enable")
def enable(payload: Registration, request: Request):
    calls, login = access(request)
    require_supported(request, android_only=True)
    name = " ".join(payload.name.split())
    if not name:
        raise error(400, "invalid_name", "Введите имя")
    device_id = str(payload.device_id)
    with closing(calls.connect()) as db, db:
        db.execute("DELETE FROM devices WHERE device_id = ? OR token = ?", (device_id, payload.token))
        db.execute("""INSERT INTO devices (login, name, device_id, token) VALUES (?, ?, ?, ?)
                       ON CONFLICT(login) DO UPDATE SET
                         name = excluded.name, device_id = excluded.device_id, token = excluded.token""",
                   (login, name, device_id, payload.token))
    return {"status": "ok"}


@router.post("/refresh")
def refresh(payload: Refresh, request: Request):
    calls, login = access(request)
    with closing(calls.connect()) as db, db:
        db.execute("""UPDATE OR IGNORE devices SET token = ? WHERE login = ? AND device_id = ?""",
                   (payload.token, login, str(payload.device_id)))
    return {"status": "ok"}


@router.post("/disable")
async def disable(payload: Device, request: Request):
    calls, login = access(request)
    async with calls.lock:
        with closing(calls.connect()) as db, db:
            removed = db.execute("DELETE FROM devices WHERE login = ? AND device_id = ?",
                                 (login, str(payload.device_id))).rowcount
        if removed:
            for call in calls.calls.values():
                if call.callee.login == login and call.status == "ringing":
                    call.status = "unreachable"
    return {"status": "ok"}


@router.post("/dial", response_model=CallView)
async def dial(payload: Dial, request: Request):
    calls, login = access(request)
    require_supported(request)
    if payload.login == login:
        raise error(400, "self_call", "Выберите коллегу")
    async with calls.lock:
        calls.cleanup()
        if payload.id in calls.calls:
            existing = calls.find(payload.id, login)
            if existing.caller.login != login or existing.callee.login != payload.login:
                raise error(409, "call_conflict", "Попробуйте позвонить ещё раз")
            return existing.view(login)
        if not calls.dial_limit.allow(login):
            raise error(429, "too_many_requests", "Подождите минуту и позвоните снова")
        for active in calls.calls.values():
            if (active.status in ("ringing", "accepted") and active.deadline > time.time()
                and {login, payload.login}.intersection((active.caller.login, active.callee.login))):
                raise error(409, "call_busy", "Один из участников уже занят")
        callee = calls.get_device(payload.login)
        if callee is None:
            raise error(404, "not_available", "Коллега выключил входящие звонки")
        caller = calls.get_device(login)
        call = Call(
            payload.id, Contact(login=login, name=caller["name"] if caller else login),
            Contact(login=callee["login"], name=callee["name"]), time.time() + RING_SECONDS,
        )
        calls.calls[call.id] = call

    result = await calls.push.send(callee["token"], {
        "type": "incoming_call", "call_id": str(call.id),
        "name": call.caller.name, "expires_at_ms": str(int(call.deadline * 1000)),
    }, RING_SECONDS)
    if result != "sent":
        async with calls.lock:
            if call.status == "ringing":
                call.status = "unreachable"
            if result == "unregistered":
                with closing(calls.connect()) as db, db:
                    db.execute("DELETE FROM devices WHERE login = ? AND token = ?",
                               (callee["login"], callee["token"]))
    return call.view(login)


@router.get("/{call_id}", response_model=CallView)
async def get_call(call_id: UUID, request: Request):
    calls, login = access(request)
    require_supported(request)
    return calls.find(call_id, login).view(login)


@router.post("/{call_id}/state/{action}", response_model=CallView)
async def update(call_id: UUID, action: Literal["accept", "decline", "cancel", "end"],
                 request: Request, background: BackgroundTasks):
    calls, login = access(request)
    if action == "accept":
        require_supported(request, android_only=True)
    async with calls.lock:
        call = calls.find(call_id, login)
        incoming = call.callee.login == login
        if (action in ("accept", "decline") and not incoming) or (action == "cancel" and incoming):
            raise error(403, "call_action_forbidden", "Действие недоступно")
        if action == "accept" and call.status == "ringing":
            call.status = "accepted"
            call.deadline = time.time() + CONNECT_SECONDS
        elif action == "decline" and call.status == "ringing":
            call.status = "declined"
        elif action == "cancel" and call.status in ("ringing", "accepted"):
            target = calls.get_device(call.callee.login)
            if target and call.status == "ringing":
                background.add_task(calls.push.send, target["token"], {
                    "type": "cancelled_call", "call_id": str(call.id),
                }, RING_SECONDS)
            call.status = "cancelled"
            await calls.close_room(call, request)
        elif action == "end" and call.status == "accepted":
            call.status = "ended"
            await calls.close_room(call, request)
        return call.view(login)
