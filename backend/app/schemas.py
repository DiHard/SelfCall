from pydantic import BaseModel, Field


class Room(BaseModel):
    name: str


class RoomsResponse(BaseModel):
    rooms: list[Room]


class TokenRequest(BaseModel):
    room: str = Field(min_length=1, max_length=64)
    username: str = Field(min_length=1, max_length=64)


class TokenResponse(BaseModel):
    token: str
    url: str
    room: str
    identity: str
