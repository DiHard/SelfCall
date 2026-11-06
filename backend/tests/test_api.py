import os
import time

os.environ.setdefault("LIVEKIT_API_KEY", "devkey")
os.environ.setdefault("LIVEKIT_API_SECRET", "secret-for-tests-only-0123456789")
os.environ.setdefault("LIVEKIT_URL", "ws://localhost:7880")

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app import meeting_id  # noqa: E402
from app.config import settings  # noqa: E402
from app.main import app, close_expired_meetings  # noqa: E402
from app.rooms import RoomState  # noqa: E402


class FakeRooms:
    def __init__(self) -> None:
        self.rooms: dict[str, RoomState] = {}

    async def list_rooms(self) -> list[RoomState]:
        return list(self.rooms.values())

    async def create_room(self, name: str) -> None:
        self.rooms[name] = RoomState(name=name, participants=0, created_at=time.time())

    async def delete_room(self, name: str) -> None:
        self.rooms.pop(name, None)

    async def aclose(self) -> None:
        pass


@pytest.fixture
def fake() -> FakeRooms:
    return FakeRooms()


@pytest.fixture
def client(fake: FakeRooms):
    app.state.rooms = fake
    app.state.create_limiter.reset()
    app.state.join_limiter.reset()
    with TestClient(app) as c:
        yield c


def test_meeting_id_roundtrip() -> None:
    secret = "s"
    mid = meeting_id.new_meeting_id(secret)
    assert len(mid) == 12 and mid.count("-") == 2
    assert meeting_id.is_valid(mid, secret)
    assert not meeting_id.is_valid(mid, "other-secret")
    tampered = ("b" if mid[0] != "b" else "c") + mid[1:]
    assert not meeting_id.is_valid(tampered, secret)
    assert not meeting_id.is_valid("room-1", secret)


def test_create_and_join(client: TestClient, fake: FakeRooms) -> None:
    r = client.post("/api/meetings")
    assert r.status_code == 201
    mid = r.json()["id"]
    assert mid in fake.rooms

    info = client.get(f"/api/meetings/{mid.upper()}").json()
    assert info == {"id": mid, "active": True, "participants": 0, "max_participants": settings.max_participants}

    j = client.post(f"/api/meetings/{mid}/join", json={}).json()
    assert j["name"] == "Гость"
    assert j["meeting_id"] == mid
    assert j["token"]

    named = client.post(f"/api/meetings/{mid}/join", json={"name": "  Максим "}).json()
    assert named["name"] == "Максим"
    assert named["identity"] != j["identity"]


def test_unknown_meeting_is_404(client: TestClient) -> None:
    r = client.get("/api/meetings/abc-defg-hjk")
    assert r.status_code == 404
    assert r.json()["detail"]["code"] == "meeting_not_found"
    assert client.post("/api/meetings/nonsense/join", json={}).status_code == 404


def test_closed_meeting_reopens_on_join(client: TestClient, fake: FakeRooms) -> None:
    mid = client.post("/api/meetings").json()["id"]
    fake.rooms.clear()  # LiveKit closed the empty room
    assert client.get(f"/api/meetings/{mid}").json()["active"] is False
    assert client.post(f"/api/meetings/{mid}/join", json={}).status_code == 200
    assert mid in fake.rooms


def test_active_meeting_limit(client: TestClient, fake: FakeRooms) -> None:
    ids = [client.post("/api/meetings").json()["id"] for _ in range(settings.max_active_meetings)]
    r = client.post("/api/meetings")
    assert r.status_code == 503
    assert r.json()["detail"]["code"] == "server_busy"

    # An existing meeting can still be joined
    assert client.post(f"/api/meetings/{ids[0]}/join", json={}).status_code == 200
    # A closed one can't be reopened while the server is full
    fake.rooms.pop(ids[0])
    fake.rooms["someone-else"] = RoomState("someone-else", 1, time.time())
    assert client.post(f"/api/meetings/{ids[0]}/join", json={}).json()["detail"]["code"] == "server_busy"


def test_full_meeting(client: TestClient, fake: FakeRooms) -> None:
    mid = client.post("/api/meetings").json()["id"]
    fake.rooms[mid].participants = settings.max_participants
    r = client.post(f"/api/meetings/{mid}/join", json={})
    assert r.status_code == 409
    assert r.json()["detail"]["code"] == "meeting_full"


def test_create_rate_limit(client: TestClient, fake: FakeRooms) -> None:
    codes = []
    for _ in range(settings.create_per_ip_per_hour + 1):
        r = client.post("/api/meetings", headers={"X-Real-IP": "1.2.3.4"})
        codes.append(r.status_code)
        fake.rooms.clear()  # keep under the active-meeting limit
    assert codes[-1] == 429
    assert client.post("/api/meetings", headers={"X-Real-IP": "5.6.7.8"}).status_code == 201


@pytest.mark.anyio
async def test_expired_meetings_are_closed(fake: FakeRooms) -> None:
    fake.rooms["old"] = RoomState("old", 2, time.time() - settings.max_meeting_hours * 3600 - 5)
    fake.rooms["new"] = RoomState("new", 2, time.time())
    assert await close_expired_meetings(fake) == ["old"]
    assert list(fake.rooms) == ["new"]


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"
