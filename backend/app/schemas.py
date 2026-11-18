from pydantic import BaseModel, Field


class MeetingCreated(BaseModel):
    id: str


class MeetingInfo(BaseModel):
    id: str
    active: bool
    participants: int
    max_participants: int


class JoinRequest(BaseModel):
    # Optional: people may join without typing a name
    name: str | None = Field(default=None, max_length=40)


class JoinResponse(BaseModel):
    token: str
    url: str
    meeting_id: str
    identity: str
    name: str
    elapsed_ms: int
