# SelfCall

Свой сервис видеовстреч по ссылке для нескольких человек, по образцу Яндекс Телемоста:

- создал встречу → получил ссылку → отправил;
- собеседник открывает ссылку и входит без регистрации и без ввода имени;
- есть камера, микрофон, демонстрация экрана и выбор устройств.

Сервис открыт всем, поэтому на сервере стоят лимиты:
- не больше 10 встреч одновременно;
- не больше 10 человек во встрече;
- пустые встречи закрываются сами;
- одна встреча длится не дольше 8 часов.

Медиа идёт через **LiveKit** (SFU), бэкенд на **FastAPI**, веб-клиент на **React**. Дальше по плану клиенты для Android и iOS: ссылка из Telegram будет открываться в приложении.

## Структура репо

```
SelfCall/
├── AGENTS.md       ← памятка для AI-агентов: команды, структура
├── ROADMAP.md      ← план работ с чек-листом
├── STATUS.md       ← текущий статус и «как продолжить»
├── backend/        ← FastAPI: встречи, токены, лимиты
├── frontend/       ← React + Vite + LiveKit Components
├── android/        ← Kotlin + Compose (пока старая версия с комнатами)
└── infra/
    ├── local/      ← Docker Compose для запуска на своём компьютере
    └── ...         ← продакшен: LiveKit + coturn + Nginx + certbot
```

## Локальный запуск

Нужен Docker. На Mac это Docker Desktop или Colima (`brew install colima docker docker-compose`, затем `colima start`).

```bash
docker compose -f infra/local/docker-compose.yml up --build -d
```

Откройте http://localhost:8080 и создайте встречу. Второго участника проще всего добавить из окна инкогнито (Cmd+Shift+N в Chrome), вставив ту же ссылку.

Без второго человека можно подключить тестового бота с видео:

```bash
docker run --rm --network selfcall-local_default livekit/livekit-cli:latest room join --url ws://livekit:7880 --api-key devkey --api-secret local-dev-secret-0123456789abcdef0123456789 --identity bot --publish-demo <код-встречи>
```

Остановить всё:

```bash
docker compose -f infra/local/docker-compose.yml down
```

Локальная сборка работает только на этом компьютере (`localhost`). С телефона по сети не зайти: браузеры дают камеру только по HTTPS.

## Архитектура

```
                 ┌──────────────── один сервер ────────────────┐
  Браузер ─────► │ nginx ── /         → React (статика)         │
  Android ─────► │       └─ /api/     → FastAPI (встречи, токены)│
                 │ LiveKit (WebRTC) ◄── медиа UDP/TCP            │
                 │ coturn (TURN) — для строгих сетей             │
                 └──────────────────────────────────────────────┘
```

## Документация

- **Что сделано и что дальше:** [STATUS.md](STATUS.md), [ROADMAP.md](ROADMAP.md)
- **Бэкенд, API и лимиты:** [backend/README.md](backend/README.md)
- **Фронт:** [frontend/README.md](frontend/README.md)
- **Деплой на сервер:** [infra/README.md](infra/README.md) (будет обновлён на этапе 3)
