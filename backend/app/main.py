from datetime import timedelta

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from livekit import api

from .config import settings
from .schemas import Room, RoomsResponse, TokenRequest, TokenResponse

app = FastAPI(title="SelfCall API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/rooms", response_model=RoomsResponse)
def list_rooms() -> RoomsResponse:
    return RoomsResponse(rooms=[Room(name=r) for r in settings.rooms])


@app.post("/token", response_model=TokenResponse)
def create_token(payload: TokenRequest) -> TokenResponse:
    if payload.room not in settings.rooms:
        raise HTTPException(status_code=400, detail="Unknown room")

    token = (
        api.AccessToken(settings.livekit_api_key, settings.livekit_api_secret)
        .with_identity(payload.username)
        .with_name(payload.username)
        .with_ttl(timedelta(minutes=settings.token_ttl_minutes))
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=payload.room,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True,
            )
        )
        .to_jwt()
    )

    return TokenResponse(
        token=token,
        url=settings.livekit_url,
        room=payload.room,
        identity=payload.username,
    )
