# SelfCall — Backend

FastAPI service that:
1. Returns the list of 5 fixed rooms (`GET /rooms`)
2. Issues LiveKit JWT access tokens (`POST /token`)

No database, no authentication — a username is anything the client sends.

## Local development

```bash
cd backend
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp .env.example .env
# Fill LIVEKIT_API_KEY / LIVEKIT_API_SECRET with values from infra/.env

uvicorn app.main:app --reload --port 8000
```

Then open http://localhost:8000/docs for the interactive Swagger UI.

## Endpoints

### `GET /health`
```json
{"status": "ok"}
```

### `GET /rooms`
```json
{"rooms": [{"name": "room-1"}, ..., {"name": "room-5"}]}
```

### `POST /token`
Request:
```json
{"room": "room-1", "username": "Alice"}
```
Response:
```json
{
  "token": "eyJhbGciOi...",
  "url": "wss://livekit.if-x.ru",
  "room": "room-1",
  "identity": "Alice"
}
```

## Deployment

The backend will be added to `infra/docker-compose.yml` as a new service
and exposed on `127.0.0.1:8000` (nginx proxies `api.if-x.ru` to it).
