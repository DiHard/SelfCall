# SelfCall — Backend

FastAPI-сервис: создаёт встречи, выдаёт токены LiveKit, следит за лимитами. Без базы данных и без авторизации.

## Как устроены встречи

- **Встреча = комната LiveKit.** Бэкенд создаёт её через API LiveKit. В LiveKit выключено автосоздание комнат (`room.auto_create: false`), поэтому обойти лимит, подключившись напрямую, нельзя.
- **ID встречи** выглядит как `abc-defg-hjk`: 7 случайных букв и 3 буквы подписи (HMAC). Сервер проверяет подпись без хранения ID, так что ссылка работает всегда. Если встреча уже закрылась, при входе по ссылке она создаётся заново.
- **Автозавершение** делает сам LiveKit:
  - встречу, в которую никто не пришёл, — через 10 минут (`EMPTY_TIMEOUT_SECONDS`);
  - встречу, из которой все вышли, — через 2 минуты (`DEPARTURE_TIMEOUT_SECONDS`).
- **Жёсткий предел длительности** — 8 часов (`MAX_MEETING_HOURS`). Фоновая задача раз в минуту закрывает встречи старше.
- **Лимиты:**
  - 10 встреч одновременно на сервере;
  - 10 человек во встрече;
  - 20 созданий встреч в час с одного IP;
  - 30 попыток входа в минуту с одного IP.

  Все значения настраиваются в `.env`, см. [.env.example](.env.example).

## API

| Метод | Путь | Что делает |
|-------|------|-----------|
| `GET` | `/api/health` | `{"status":"ok"}` |
| `POST` | `/api/meetings` | Создаёт встречу → `{"id": "abc-defg-hjk"}` |
| `GET` | `/api/meetings/{id}` | `{"id", "active", "participants", "max_participants"}` |
| `POST` | `/api/meetings/{id}/join` | Тело `{"name": "Максим"}` (имя необязательно, по умолчанию «Гость») → `{"token", "url", "meeting_id", "identity", "name"}` |

Ошибки приходят в виде `{"detail": {"code": "...", "message": "..."}}`. Клиенты показывают свой текст по полю `code`:

| HTTP | `code` | Когда |
|------|--------|-------|
| 404 | `meeting_not_found` | ID с ошибкой или чужая подпись |
| 409 | `meeting_full` | Во встрече нет мест |
| 429 | `too_many_requests` | Превышен лимит запросов с IP |
| 503 | `server_busy` | Уже идёт максимум встреч |

## Локальный запуск

Проще всего поднять весь сервис через Docker — см. корневой [README](../README.md#локальный-запуск).

Отдельно, без Docker (нужен запущенный LiveKit):

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env    # ключи и адрес LiveKit
uvicorn app.main:app --reload --port 8000
```

Swagger: http://localhost:8000/docs

## Тесты

```bash
cd backend
pytest -q
```

Или в Docker, без локального Python:

```bash
docker run --rm -v "$PWD/backend":/app -w /app python:3.12-slim sh -c "pip install -q -r requirements-dev.txt && pytest -q"
```
