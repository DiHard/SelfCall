# AGENTS.md

Памятка для AI-агентов (Claude Code, Codex и др.) по монорепо **SelfCall** (свой сервис видеовстреч по ссылке, как Яндекс Телемост, на LiveKit).

Общий обзор, архитектура, статус и план — в [README.md](README.md), [STATUS.md](STATUS.md), [ROADMAP.md](ROADMAP.md).

## Структура

```
backend/    FastAPI (Python 3) — встречи по ссылке, токены LiveKit, лимиты
frontend/   React + Vite + TypeScript
android/    Kotlin + Jetpack Compose + LiveKit: встречи по ссылке, как на сайте
infra/      Docker Compose для сервера: LiveKit + backend + web (за nginx хоста)
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

Открывать папку `android/` в Android Studio (не корень репо). AGP 9.5, Gradle 9.8, compileSdk 37.

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug              # debug-APK → app/build/outputs/apk/debug/
./gradlew installDebug               # установить на подключённое устройство
./gradlew lint
```

Адрес сервиса (сайт, `/api` и ссылки `/m/...` на одном домене) — по умолчанию `https://call.gamma.help`.
Для локального Docker: `./gradlew installDebug -PSELFCALL_SERVER=http://10.0.2.2:8080`. Подробнее: [android/README.md](android/README.md).

### Infra (`infra/`)

Продакшен — `https://call.gamma.help` на офисном сервере `araratadmin@188.164.152.23` (LAN `192.168.10.13`). **На нём же работает продакшен Gamma (Аврора): её nginx-сайты, Postgres, контейнеры и VM не трогать.** SelfCall использует `~/selfcall`, Docker-проект `selfcall`, сборщик `selfcall-build`, свой сайт nginx и порты 127.0.0.1:8110, TCP 7881, UDP 7882.

Команда пользователя «давай делать выкладку» означает выполнить обычную выкладку SelfCall на продакшен до конца. Следуй [порядку выкладки](infra/README.md#выкладка-на-продакшен): проверь текущие изменения, запусти с Mac `bash infra/scripts/deploy.sh` и проверь результат на сервере. Скрипт берёт файлы из рабочей папки, включая незакоммиченные изменения. Сборка сайта и API идёт через отдельный сборщик с лимитом в одно ядро и 2 ГиБ памяти без подкачки. Android-приложение выпускается отдельно.
