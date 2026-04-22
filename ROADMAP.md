# SelfCall — план работ

Проект: self-hosted сервер 1-на-1 видео/аудио звонков с кросс-платформенными клиентами.

**Стек:** LiveKit (SFU) + coturn (TURN) + Nginx + FastAPI (Python) + React (Web) + Kotlin (Android).

**Принцип работы:** пользователь вводит имя → видит список из 5 фиксированных комнат → заходит в любую → общается 1-на-1.

---

## Домены

| Поддомен | Назначение |
|----------|-----------|
| `app.if-x.ru` | React приложение (статика) |
| `api.if-x.ru` | Python бэкенд (FastAPI) |
| `livekit.if-x.ru` | LiveKit WebSocket + WebRTC |

---

## Этап 0 — Репозиторий

- [x] Создать структуру папок (`backend/`, `frontend/`, `android/`, `infra/`)
- [x] Инициализировать git, настроить `.gitignore`
- [x] Создать публичный репозиторий на GitHub ([DiHard/SelfCall](https://github.com/DiHard/SelfCall))
- [x] Первый push
- [x] Зафиксировать план работ в `ROADMAP.md`

---

## Этап 1 — Инфраструктура (VPS)

### 1.1. Заказ VPS и DNS
- [ ] Купить VPS у NetAngels (тариф Старт-3, Ubuntu 24.04)
- [ ] Получить публичный IP
- [ ] Создать A-записи в DNS: `app.if-x.ru`, `api.if-x.ru`, `livekit.if-x.ru` → IP VPS

### 1.2. Подготовка локальных конфигов (можно делать до получения VPS)
- [ ] `infra/docker-compose.yml` — LiveKit + coturn + Nginx
- [ ] `infra/livekit/livekit.yaml` — конфиг LiveKit
- [ ] `infra/coturn/turnserver.conf` — конфиг TURN-сервера
- [ ] `infra/nginx/` — конфиги для 3 поддоменов
- [ ] `infra/scripts/setup.sh` — первичная настройка сервера (firewall, Docker, swap)
- [ ] `infra/.env.example` — шаблон переменных окружения (ключи, домены)
- [ ] `infra/README.md` — инструкция по деплою

### 1.3. Настройка сервера
- [ ] SSH-доступ по ключу, отключить парольный вход
- [ ] Firewall (ufw): 22, 80, 443, 7881/tcp, 7882/udp, 3478/udp, 50000-60000/udp
- [ ] Установить Docker + Docker Compose
- [ ] Настроить swap 1 ГБ (для тарифа с 2 ГБ RAM)

### 1.4. Деплой LiveKit + coturn
- [ ] Склонировать репозиторий на сервер
- [ ] Сгенерировать секреты (API key/secret, TURN password)
- [ ] Запустить `docker compose up -d`
- [ ] Проверить, что LiveKit доступен локально на сервере

### 1.5. SSL и Nginx
- [ ] Установить certbot, получить сертификаты для 3 поддоменов
- [ ] Настроить Nginx как reverse proxy
- [ ] Проверить: `wss://livekit.if-x.ru` отвечает
- [ ] Проверить: TURN работает через публичный IP

**Результат этапа:** LiveKit доступен по `wss://livekit.if-x.ru`

---

## Этап 2 — Python бэкенд (FastAPI)

- [ ] Структура проекта (`backend/app/`, `requirements.txt`, `Dockerfile`)
- [ ] Конфиг через `.env` (LiveKit API key/secret, URL, CORS origins)
- [ ] `GET /rooms` → список 5 фиксированных комнат
- [ ] `POST /token` → генерация JWT для LiveKit
- [ ] CORS для `app.if-x.ru` и Android
- [ ] Добавить сервис `backend` в `docker-compose.yml`
- [ ] Nginx: проксирование `api.if-x.ru` → `backend:8000`
- [ ] Деплой, проверка через curl

**Результат этапа:** API публично доступно, выдаёт рабочие токены

---

## Этап 3 — React приложение

- [ ] Инициализация через Vite + React + TypeScript
- [ ] Зависимости: `@livekit/components-react`, `livekit-client`
- [ ] Экран ввода имени (сохранение в localStorage)
- [ ] Экран списка комнат (запрос `GET /rooms`)
- [ ] Экран звонка (компонент `LiveKitRoom`, видео-тайлы, кнопки mute/камера/выход)
- [ ] Роутинг между экранами
- [ ] Сборка `dist/`, Nginx отдаёт статику на `app.if-x.ru`
- [ ] Проверка React ↔ React звонка

**Результат этапа:** работающий веб-клиент на `https://app.if-x.ru`

---

## Этап 4 — Android приложение

- [ ] Установить Android Studio
- [ ] Создать проект (Kotlin, Jetpack Compose, minSdk 24)
- [ ] Зависимость `io.livekit:livekit-android`
- [ ] Разрешения: CAMERA, RECORD_AUDIO, INTERNET
- [ ] Экран ввода имени (SharedPreferences)
- [ ] Экран списка комнат (Retrofit/Ktor к `api.if-x.ru`)
- [ ] Экран звонка (LocalVideoTrack, RemoteVideoTrack, контролы)
- [ ] Runtime-запрос разрешений
- [ ] Сборка APK, тест на реальном устройстве

**Результат этапа:** APK для Android с рабочими звонками

---

## Этап 5 — Проверка функционала

- [ ] Звонок React ↔ React (один браузер — две вкладки/окна инкогнито)
- [ ] Звонок Android ↔ Android (два устройства)
- [ ] Звонок React ↔ Android (кросс-платформа)
- [ ] Проверка через мобильный интернет (NAT traversal через TURN)
- [ ] Проверка качества связи, задержки

**Результат этапа:** связка работоспособна — гипотеза подтверждена

---

## Дальнейшие идеи (после валидации)

- Переезд на более мощный VPS
- Групповые звонки
- Авторизация пользователей
- Чат в комнате
- Демонстрация экрана
- Запись звонков
- Push-уведомления о входящих звонках
- iOS клиент
