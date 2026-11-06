# AGENTS.md

Памятка для AI-агентов (Claude Code, Codex и др.) по монорепо **SelfCall** (свой сервис видеовстреч по ссылке, как Яндекс Телемост, на LiveKit).

Общий обзор, архитектура, статус и план — в [README.md](README.md), [STATUS.md](STATUS.md), [ROADMAP.md](ROADMAP.md).

## Структура

```
backend/    FastAPI (Python 3) — встречи по ссылке, токены LiveKit, лимиты
frontend/   React + Vite + TypeScript
android/    Kotlin + Jetpack Compose (старая версия с комнатами, ещё не переведена на новый API)
infra/      Docker Compose: LiveKit + coturn + Nginx + certbot (продакшен)
infra/local/  Docker Compose для локального запуска всего сервиса
```

## Локальный запуск всего сервиса (Docker)

```bash
docker compose -f infra/local/docker-compose.yml up --build -d   # → http://localhost:8080
docker compose -f infra/local/docker-compose.yml down
```

На Mac Docker работает через Colima: если `docker` не отвечает, сначала `colima start`.

## Соглашения

- Интерфейс только на русском. Перед изменениями UI прогонять скиллы better-* (better-interface и профильные).
- Цвета — только через токены в `frontend/src/index.css`. Пары контраста там проверены по WCAG: при смене цвета проверять заново.
- Ошибки API приходят как `{"detail": {"code", "message"}}`, клиенты показывают свой текст по `code`.
- Комнаты LiveKit создаёт только бэкенд (`room.auto_create: false`), иначе не работает лимит встреч.

## Команды запуска и сборки

### Backend (`backend/`)

```bash
python -m venv .venv
source .venv/bin/activate           # macOS / Linux (Windows: .venv\Scripts\activate)
pip install -r requirements-dev.txt
cp .env.example .env                # заполнить LIVEKIT_API_KEY/SECRET/URL
uvicorn app.main:app --reload --port 8000
pytest -q                           # тесты
```

- Swagger: http://localhost:8000/docs
- Docker-образ: `docker build -t selfcall-backend backend/`
- Подробнее: [backend/README.md](backend/README.md)

### Frontend (`frontend/`)

```bash
npm install
cp .env.example .env                # API по /api, Vite проксирует его на бэкенд
npm run dev                         # dev-сервер на http://localhost:5173
npm run build                       # tsc -b && vite build → dist/
npm run lint                        # eslint
npm run preview                     # предпросмотр собранного build
```

- Подробнее: [frontend/README.md](frontend/README.md)

### Android (`android/`)

Открывать папку `android/` в Android Studio (не корень репо). Gradle wrapper JAR не закоммичен — при первом sync AS предложит сгенерировать, согласиться.

CLI-сборка (после успешного sync):

```bash
cd android
./gradlew assembleDebug              # debug-APK → app/build/outputs/apk/debug/
./gradlew installDebug               # установить на подключённое устройство
./gradlew lint
```

Переопределить backend URL (для эмулятора / LAN-устройства) — через `android/gradle.properties`:

```
SELFCALL_API_BASE=http://10.0.2.2:8000
```

По умолчанию — `https://api.if-x.ru`. Подробнее: [android/README.md](android/README.md).

### Infra (`infra/`)

Деплой только на VPS (host networking, certbot). Локально НЕ запускается.

```bash
cd infra
bash scripts/01-server-init.sh       # Docker + firewall + swap (один раз)
bash scripts/gen-secrets.sh          # сгенерировать ключи
cp .env.example .env                 # вставить секреты, домены
bash scripts/02-issue-certs.sh       # Let's Encrypt (после первого старта nginx)
docker compose up -d
docker compose logs -f livekit
```

Пошаговая инструкция с нюансами (отключение TLS-конфигов на время выпуска сертов и т.п.): [infra/README.md](infra/README.md).

## Быстрый smoke-test после деплоя

```bash
curl https://api.if-x.ru/health      # {"status":"ok"}
curl -X POST https://api.if-x.ru/api/meetings   # {"id":"abc-defg-hjk"}
curl -I https://livekit.if-x.ru      # LiveKit отвечает OK
```
