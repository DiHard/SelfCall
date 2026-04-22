# SelfCall — Frontend

React SPA (Vite + TypeScript + LiveKit Components).

Three screens:
1. **`/`** — enter display name (saved to localStorage)
2. **`/rooms`** — list of 5 fixed rooms from `GET /rooms`
3. **`/room/:roomName`** — video call via `<LiveKitRoom>` + `<VideoConference>`

## Local development

Prerequisite: backend running on `http://localhost:8000`.

```bash
cd frontend
npm install
cp .env.example .env   # VITE_API_BASE_URL defaults to localhost:8000
npm run dev
```

Open http://localhost:5173 in two browser windows (one regular, one incognito)
to test a 1-on-1 call locally against LiveKit Cloud dev creds or your VPS.

## Build

```bash
npm run build
# Output in dist/ — static HTML/JS/CSS, copy to any web server
```

## Deployment

The `dist/` contents are mounted into the nginx container via a named volume
(`frontend_dist`) and served from `https://app.if-x.ru`.
