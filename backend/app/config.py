from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    livekit_api_key: str
    livekit_api_secret: str
    # Public WebSocket URL that browsers / apps connect to (wss://livekit.example.com)
    livekit_url: str
    # URL the backend uses to call the LiveKit server API. Defaults to livekit_url.
    livekit_api_url: str = ""

    cors_origins: str = "http://localhost:5173"

    # Secret used to sign meeting IDs. Defaults to the LiveKit API secret.
    meeting_secret: str = ""

    # ----- Limits -----
    # How many meetings may exist at the same time on the server
    max_active_meetings: int = 10
    # How many people may be in one meeting
    max_participants: int = 10
    # A created meeting nobody joined is closed after this many seconds
    empty_timeout_seconds: int = 600
    # A meeting is closed this many seconds after the last person left
    departure_timeout_seconds: int = 120
    # A meeting is force-closed after this many hours
    max_meeting_hours: int = 8
    # Meeting creations allowed per IP per hour
    create_per_ip_per_hour: int = 20
    # Join requests allowed per IP per minute
    join_per_ip_per_minute: int = 30

    token_ttl_minutes: int = 15

    fcm_credentials_file: str = ""
    direct_calls_proxy_secret: str = ""
    direct_calls_database: str = "/var/lib/selfcall/direct-calls.sqlite3"
    android_releases_dir: Path | None = None

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def api_url(self) -> str:
        url = self.livekit_api_url or self.livekit_url
        return url.replace("wss://", "https://").replace("ws://", "http://")

    @property
    def signing_secret(self) -> str:
        return self.meeting_secret or self.livekit_api_secret


settings = Settings()  # type: ignore[call-arg]
