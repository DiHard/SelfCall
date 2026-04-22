from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    livekit_api_key: str
    livekit_api_secret: str
    livekit_url: str

    cors_origins: str = "http://localhost:5173"
    token_ttl_minutes: int = 60

    # Fixed list of rooms users can join
    rooms: list[str] = [
        "room-1",
        "room-2",
        "room-3",
        "room-4",
        "room-5",
    ]

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


settings = Settings()  # type: ignore[call-arg]
