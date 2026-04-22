# SelfCall — текущий статус

> Этот файл — точка входа, если возвращаешься к проекту на другой машине
> или через неделю. Читай сверху вниз. Подробный чек-лист — в [ROADMAP.md](ROADMAP.md).

## Что готово (код)

| Компонент | Состояние |
|-----------|-----------|
| **infra/** — Docker Compose, LiveKit, coturn, Nginx, certbot, скрипты init | ✅ Полностью написан, **не задеплоен** (ждём VPS) |
| **backend/** — FastAPI: `GET /health`, `GET /rooms`, `POST /token` | ✅ Локально smoke-тестирован, JWT валиден |
| **frontend/** — React + Vite + `@livekit/components-react` | ✅ `tsc -b` и `vite build` проходят |
| **android/** — Kotlin + Compose + `livekit-android` + `livekit-android-compose-components` | ⚠️ Scaffold написан вслепую (без Android Studio), gradle sync ещё не прогонялся |

## Что блокирует продолжение

1. **Нет VPS.** Заказан тариф **Старт-3** у **NetAngels** (Ubuntu 24.04, 2 vCPU / 2 GB RAM / 10 GB). Ждём получения IP.
2. **Нет Android Studio.** Пока не ставили — нужно для gradle sync и сборки APK.

## Как продолжить работу на другой машине

### Первое клонирование

```bash
git clone git@github.com:DiHard/SelfCall.git
cd SelfCall
```

### Локальная разработка backend

```bash
cd backend
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
# Заполнить LIVEKIT_API_KEY / LIVEKIT_API_SECRET / LIVEKIT_URL
uvicorn app.main:app --reload --port 8000
```

### Локальная разработка frontend

```bash
cd frontend
npm install
cp .env.example .env             # VITE_API_BASE_URL=http://localhost:8000
npm run dev
```

### Android

Открыть папку `android/` в Android Studio. Gradle wrapper JAR не закоммичен,
поэтому при первом sync AS попросит его сгенерировать — согласиться.
Подробности: [android/README.md](android/README.md).

## Следующий конкретный шаг

**После получения IP от NetAngels:**

1. Создать три A-записи в DNS панели NetAngels:
   - `app.if-x.ru`    → IP
   - `api.if-x.ru`    → IP
   - `livekit.if-x.ru` → IP
2. Дождаться распространения (5–30 минут). Проверить: `nslookup app.if-x.ru`.
3. Пройти по [infra/README.md](infra/README.md) — там пошаговый деплой:
   - SSH на VPS
   - клонировать репо
   - `bash scripts/01-server-init.sh` (Docker, ufw, swap)
   - `bash scripts/gen-secrets.sh` → вставить в `.env`
   - Выпустить TLS-сертификаты через certbot
   - `docker compose up -d`
4. Локальный smoke-тест: `curl https://api.if-x.ru/rooms`
5. Сбилдить фронт и положить `dist/` в volume `frontend_dist`
6. Открыть `https://app.if-x.ru` в двух окнах — первый React↔React звонок

**После этого:**
- Поднять Android Studio, открыть `android/`, запустить на телефоне
- Кросс-платформенный тест: React ↔ Android

## Ключевые решения по проекту

- **Monorepo** — проще синхронизировать изменения API между клиентами
- **Single VPS, host networking для LiveKit/coturn/Nginx** — UDP media через
  Docker userland proxy даёт packet loss; host net этого избегает
- **3 поддомена** — чистое разделение, у каждого свой сертификат, независимый деплой
- **5 фиксированных комнат без авторизации** — минимум для проверки гипотезы;
  потом можно добавить динамические комнаты + авторизацию
- **minSdk 26 на Android** — позволяет обойтись adaptive иконками без PNG-фолбэков

## Полезные ссылки

- Репо: https://github.com/DiHard/SelfCall
- LiveKit docs: https://docs.livekit.io/home/self-hosting/
- LiveKit Android SDK: https://github.com/livekit/client-sdk-android
- LiveKit Components Android: https://github.com/livekit/components-android
