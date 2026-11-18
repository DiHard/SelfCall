"""Thin wrapper over the LiveKit room API, so tests can swap in a fake."""

from dataclasses import dataclass
from typing import Protocol

from livekit import api

from .config import Settings


@dataclass
class RoomState:
    name: str
    participants: int
    created_at: float  # unix seconds
    started_at_ms: int | None = None


class RoomService(Protocol):
    async def list_rooms(self) -> list[RoomState]: ...
    async def create_room(self, name: str, started_at_ms: int | None = None) -> None: ...
    async def set_start_time(self, name: str, started_at_ms: int) -> None: ...
    async def delete_room(self, name: str) -> None: ...
    async def aclose(self) -> None: ...


class LiveKitRoomService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._api: api.LiveKitAPI | None = None

    def _client(self) -> api.LiveKitAPI:
        # Created lazily: LiveKitAPI needs a running event loop.
        if self._api is None:
            self._api = api.LiveKitAPI(
                self._settings.api_url,
                self._settings.livekit_api_key,
                self._settings.livekit_api_secret,
            )
        return self._api

    async def list_rooms(self) -> list[RoomState]:
        res = await self._client().room.list_rooms(api.ListRoomsRequest())
        return [
            RoomState(
                name=r.name,
                participants=r.num_participants,
                created_at=float(r.creation_time),
                started_at_ms=int(r.metadata) if r.metadata.isascii() and r.metadata.isdecimal() else None,
            )
            for r in res.rooms
        ]

    async def create_room(self, name: str, started_at_ms: int | None = None) -> None:
        s = self._settings
        await self._client().room.create_room(
            api.CreateRoomRequest(
                name=name,
                empty_timeout=s.empty_timeout_seconds,
                departure_timeout=s.departure_timeout_seconds,
                max_participants=s.max_participants,
                metadata=str(started_at_ms) if started_at_ms is not None else "",
            )
        )

    async def set_start_time(self, name: str, started_at_ms: int) -> None:
        await self._client().room.update_room_metadata(
            api.UpdateRoomMetadataRequest(room=name, metadata=str(started_at_ms))
        )

    async def delete_room(self, name: str) -> None:
        await self._client().room.delete_room(api.DeleteRoomRequest(room=name))

    async def aclose(self) -> None:
        if self._api is not None:
            await self._api.aclose()
