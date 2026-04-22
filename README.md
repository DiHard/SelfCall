# SelfCall

Self-hosted 1-на-1 видео/аудио звонки. Кросс-платформенно: **Web (React)**, **Android (Kotlin)**, единый бэкенд на Python. Медиа через **LiveKit** (SFU) + **coturn** (TURN).

Это тестовый проект для валидации гипотезы: поднять свой звонилку на VPS за ~500 ₽/мес.

## Структура репо

```
SelfCall/
├── ROADMAP.md      ← план работ с чек-листом
├── STATUS.md       ← текущий статус и "как продолжить"
├── infra/          ← Docker Compose: LiveKit + coturn + Nginx + certbot
├── backend/        ← FastAPI (Python) — токены + список комнат
├── frontend/       ← React + Vite + LiveKit Components
└── android/        ← Kotlin + Jetpack Compose + LiveKit Android SDK
```

## Архитектура

```
                    ┌─────────────────────────────┐
  React (web) ────► │  api.if-x.ru    → FastAPI   │ — токены
  Android SDK ────► │  livekit.if-x.ru → LiveKit  │ — WebRTC signaling
                    │  + coturn on :3478/UDP      │ — NAT traversal
                    └─────────────────────────────┘
                              (single VPS)
```

## Домены

| Поддомен | Назначение |
|----------|-----------|
| `app.if-x.ru` | React SPA (статика через Nginx) |
| `api.if-x.ru` | FastAPI бэкенд |
| `livekit.if-x.ru` | LiveKit WebSocket + WebRTC |

## Быстрый старт

- **Планирование и прогресс:** [ROADMAP.md](ROADMAP.md)
- **Где мы сейчас и что делать дальше:** [STATUS.md](STATUS.md)
- **Деплой инфры:** [infra/README.md](infra/README.md)
- **Бэкенд локально:** [backend/README.md](backend/README.md)
- **Фронт локально:** [frontend/README.md](frontend/README.md)
- **Android первый запуск:** [android/README.md](android/README.md)
