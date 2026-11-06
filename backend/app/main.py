import asyncio
import logging
import secrets
import time
from contextlib import asynccontextmanager, suppress
from datetime import timedelta

from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from livekit import api

from . import meeting_id
from .config import settings
from .rate_limit import RateLimiter
from .rooms import LiveKitRoomService, RoomService, RoomState
from .schemas import JoinRequest, JoinResponse, MeetingCreated, MeetingInfo

log = logging.getLogger("selfcall")

DEFAULT_NAME = "Гость"
CLEANUP_INTERVAL_SECONDS = 60


def _error(status: int, code: str, message: str) -> HTTPException:
    """Errors carry a stable `code` the clients map to their own wording."""
    return HTTPException(status_code=status, detail={"code": code, "message": message})


async def close_expired_meetings(rooms: RoomService) -> list[str]:
    """Close meetings that have been running longer than allowed."""
    limit = settings.max_meeting_hours * 3600
    now = time.time()
    closed: list[str] = []
    for room in await rooms.list_rooms():
        if room.created_at and now - room.created_at > limit:
            await rooms.delete_room(room.name)
            closed.append(room.name)
    return closed


async def _cleanup_loop(app: FastAPI) -> None:
    while True:
        await asyncio.sleep(CLEANUP_INTERVAL_SECONDS)
        try:
            closed = await close_expired_meetings(app.state.rooms)
            if closed:
                log.info("Closed meetings over the time limit: %s", closed)
        except Exception:  # LiveKit may be restarting; try again next round
            log.exception("Meeting cleanup failed")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not hasattr(app.state, "rooms"):
        app.state.rooms = LiveKitRoomService(settings)
    task = asyncio.create_task(_cleanup_loop(app))
    try:
        yield
    finally:
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task
        await app.state.rooms.aclose()


app = FastAPI(title="SelfCall API", version="0.2.0", lifespan=lifespan)
app.state.create_limiter = RateLimiter(settings.create_per_ip_per_hour, 3600)
app.state.join_limiter = RateLimiter(settings.join_per_ip_per_minute, 60)
# Serialises "count meetings → create one" so two requests can't both slip under the limit
app.state.lock = asyncio.Lock()

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

router = APIRouter(prefix="/api")


def _client_ip(request: Request) -> str:
    # Nginx in front of us sets X-Real-IP
    return request.headers.get("x-real-ip") or (request.client.host if request.client else "?")


def _rooms(request: Request) -> RoomService:
    return request.app.state.rooms


def _checked_id(raw: str) -> str:
    mid = meeting_id.normalize(raw)
    if not meeting_id.is_valid(mid, settings.signing_secret):
        raise _error(404, "meeting_not_found", "Встреча не найдена")
    return mid


def _find(rooms: list[RoomState], name: str) -> RoomState | None:
    return next((r for r in rooms if r.name == name), None)


@app.get("/health")
@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.post("/meetings", response_model=MeetingCreated, status_code=201)
async def create_meeting(request: Request) -> MeetingCreated:
    if not request.app.state.create_limiter.allow(_client_ip(request)):
        raise _error(429, "too_many_requests", "Слишком много встреч создано, попробуйте позже")

    rooms = _rooms(request)
    async with request.app.state.lock:
        active = await rooms.list_rooms()
        if len(active) >= settings.max_active_meetings:
            raise _error(503, "server_busy", "Сейчас идёт слишком много встреч")
        mid = meeting_id.new_meeting_id(settings.signing_secret)
        await rooms.create_room(mid)
    return MeetingCreated(id=mid)


@router.get("/meetings/{raw_id}", response_model=MeetingInfo)
async def get_meeting(raw_id: str, request: Request) -> MeetingInfo:
    mid = _checked_id(raw_id)
    room = _find(await _rooms(request).list_rooms(), mid)
    return MeetingInfo(
        id=mid,
        active=room is not None,
        participants=room.participants if room else 0,
        max_participants=settings.max_participants,
    )


@router.post("/meetings/{raw_id}/join", response_model=JoinResponse)
async def join_meeting(raw_id: str, payload: JoinRequest, request: Request) -> JoinResponse:
    if not request.app.state.join_limiter.allow(_client_ip(request)):
        raise _error(429, "too_many_requests", "Слишком много попыток, подождите минуту")
    mid = _checked_id(raw_id)

    rooms = _rooms(request)
    async with request.app.state.lock:
        active = await rooms.list_rooms()
        room = _find(active, mid)
        if room is None:
            # The link outlived its meeting: open it again if there is room on the server.
            if len(active) >= settings.max_active_meetings:
                raise _error(503, "server_busy", "Сейчас идёт слишком много встреч")
            await rooms.create_room(mid)
        elif room.participants >= settings.max_participants:
            raise _error(409, "meeting_full", "Во встрече нет свободных мест")

    name = (payload.name or "").strip() or DEFAULT_NAME
    identity = f"guest-{secrets.token_hex(4)}"
    token = (
        api.AccessToken(settings.livekit_api_key, settings.livekit_api_secret)
        .with_identity(identity)
        .with_name(name)
        .with_ttl(timedelta(minutes=settings.token_ttl_minutes))
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=mid,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True,
            )
        )
        .to_jwt()
    )
    return JoinResponse(token=token, url=settings.livekit_url, meeting_id=mid, identity=identity, name=name)


app.include_router(router)
