# SelfCall — деплой на сервер

Сервис ставится на офисный сервер, где уже работает Gamma (Аврора). Правило одно: **ничего чужого не трогать**. SelfCall добавляет на сервер только:

- папку `~/selfcall` с кодом;
- Docker-проект `selfcall` из трёх контейнеров: `livekit`, `backend`, `web`;
- отдельный Docker-сборщик `selfcall-build` с кешем сборки;
- файл `/etc/nginx/sites-available/selfcall.conf` и ссылку на него в `sites-enabled`;
- сертификат Let's Encrypt для своего домена.

## Как устроено

```
Интернет ──► роутер 188.164.152.23 ──► сервер 192.168.10.13
   443/80 ──► nginx хоста (общий с Gamma) ── <домен> ──► 127.0.0.1:8110 ─► контейнер web
                                                          ├─ /       сайт
                                                          ├─ /api/   → backend
                                                          └─ /rtc    → livekit (сигналинг)
   TCP 7881, UDP 7882 ────────────────────────────────────────────────► livekit (видео и звук)
```

Сайт, API и сигналинг LiveKit работают на одном домене, поэтому нужен один сертификат и одна DNS-запись. Видео и звук идут напрямую в LiveKit через два отдельных порта.

## Что нужно один раз

1. **Домен**, например `call.gamma.help`, и A-запись на `188.164.152.23`.
2. **Порты.** Роутер (MikroTik) уже отправляет на `192.168.10.13` весь трафик для `188.164.152.23`, так что настраивать его не нужно. Проверено 7 ноября 2026 перехватом пакетов: снаружи TCP 7881 и UDP 7882 доходят до контейнера LiveKit.

   На самом сервере есть брандмауэр nftables (таблица `inet hostfw` в `/etc/nftables.conf`): снаружи он пропускает только 22, 80 и 443. На порты Docker-контейнеров он не действует, потому что их трафик идёт через цепочку forward, а `hostfw` фильтрует только input. Поэтому LiveKit доступен без правок брандмауэра. Обратная сторона: любой порт, который контейнер публикует на `0.0.0.0`, открыт в интернет. Поэтому `web` опубликован только на `127.0.0.1`.

   Если звонят из той же офисной сети, роутер должен уметь NAT loopback (hairpin): обращение изнутри на внешний адрес `188.164.152.23` должно возвращаться на сервер.

3. **Файл `infra/.env` на сервере.** Сначала скопируйте каталоги `backend`, `frontend` и `infra` в `~/selfcall` (это первый шаг скрипта выкладки), затем на сервере:

   ```bash
   cd ~/selfcall/infra
   cp .env.example .env
   bash scripts/gen-secrets.sh   # вставить три строки в .env
   nano .env                     # DOMAIN, проверить NODE_IP
   ```

4. **Сайт в nginx хоста и сертификат**, на сервере:

   ```bash
   cd ~/selfcall/infra
   sudo bash scripts/install-nginx-site.sh   # проверяет nginx -t, при ошибке откатывает свой файл
   sudo certbot --nginx -d <домен>
   ```

## Выкладка на продакшен

1. Проверь `git status --short` и изменения в `backend`, `frontend` и `infra`, в том числе новые и незакоммиченные файлы. Скрипт копирует текущие файлы с Mac, поэтому они попадут в релиз. Прогони `pytest -q` в окружении бэкенда, `npm run lint` и `npm run build` во фронтенде. Если менялись скрипты выкладки, проверь их через `bash -n`.
2. Проверь по SSH состояние текущих контейнеров SelfCall и запиши идентификаторы образов `backend` и `web` на случай отката. Если релиз меняет LiveKit, проверь наличие текущих звонков перед его перезапуском.
3. Запусти с Mac из корня репозитория:

   ```bash
   bash infra/scripts/deploy.sh
   ```

   Скрипт отправляет `backend`, `frontend` и `infra` через `rsync` в `~/selfcall` на `araratadmin@188.164.152.23` и сохраняет серверный `infra/.env`. Затем `scripts/deploy-on-server.sh` создаёт при необходимости сборщик `selfcall-build`, проверяет общий лимит в одно ядро и 2 ГиБ памяти без подкачки и собирает `backend` и `web`. Только после успешной сборки скрипт применяет обновление через `docker compose up -d --no-build --remove-orphans` и показывает состояние контейнеров. LiveKit использует готовый образ. Если для `sudo` нужен пароль, сервер его запросит.
4. Проверь контейнеры, API и изменившиеся функции по разделу [«Проверка»](#проверка). Если сборка завершилась ошибкой, устрани причину и запусти тот же скрипт с лимитами. Если после обновления сломалась работа сервиса, посмотри логи SelfCall и при необходимости верни предыдущие рабочие образы сайта и API. После исправления или отката повтори проверку.

Обычная выкладка обновляет серверную часть SelfCall. Публикация APK и изменения действующего конфига nginx выполняются отдельно, когда они нужны для релиза. Конфигурацию «Авроры» не трогай.

## Выпуск Android

После выкладки бэкенда с поддержкой обновлений из корня репозитория на Mac:

```bash
python3 android/scripts/release.py build
python3 android/scripts/release.py publish android/releases/selfcall-5.apk
```

В команду публикации передайте путь, который напечатала сборка. Команда проверяет подпись, берёт версию из APK и передаёт файл бэкенду по SSH. Бэкенд сверяет размер и SHA‑256 и публикует проверенный файл в томе `android-releases`. В нём хранится одна текущая сборка. При публикации следующей её номер появится в новом URL. Например, сборку 5 можно скачать с сайта по пути `/api/android/releases/selfcall-5.apk` после обычного входа в SelfCall.

Публикация сохраняет действующий минимальный номер. Сборки между минимальной и последней видят добровольное обновление, сборки ниже минимальной видят экран обязательного обновления. Перед повышением порога проверьте установку APK на телефоне. Для пользователей с отладочной `0.3.0` сначала проведите ручную установку `0.4.0`.

Из `~/selfcall/infra` на сервере:

```bash
sudo docker compose exec -T backend python -m app.android_releases status
sudo docker compose exec -T backend python -m app.android_releases minimum 5
sudo docker compose exec -T backend python -m app.android_releases minimum 0
```

`minimum 5` требует как минимум сборку 5. `minimum 0` снимает требование. Изменение применяется сразу. Участники могут закончить текущий разговор. Если ошибку обнаружили в самом APK, исправление выпускается с новым, более высоким номером сборки.

## Вход по паролю

На `call.gamma.help` nginx запрашивает пароль для сайта, API и сигналинга LiveKit. Настройки находятся только в HTTPS-блоке `/etc/nginx/sites-available/selfcall.conf`. Пароли хранятся в виде хешей в `/etc/nginx/selfcall.htpasswd` (владелец `root:www-data`, права `0640`).

Если сайт nginx нужно установить заново, после выпуска сертификата восстановите в HTTPS-блоке:

```nginx
auth_basic "SelfCall";
auth_basic_user_file /etc/nginx/selfcall.htpasswd;
```

В обоих проксирующих блоках `location` добавьте `proxy_set_header Authorization "";`, чтобы nginx проверял пароль сам и LiveKit получал свой токен через строку запроса. В блоке `/.well-known/acme-challenge/` добавьте `auth_basic off;`, чтобы Certbot мог продлевать сертификат. Примените конфиг командой `sudo nginx -t && sudo systemctl reload nginx`.

### Прямые звонки в Android

Выдайте сотрудникам отдельные логины в `selfcall.htpasswd`. Служебной учётной записи
проекта Firebase нужны права на `cloudmessaging.messages.create`. Храните её JSON на
сервере в `/etc/selfcall/firebase-messaging.json` с правами `0600`, владелец `root`.
В `infra/.env` укажите `FCM_CREDENTIALS_FILE=/run/selfcall/firebase-messaging.json`
и задайте `DIRECT_CALLS_PROXY_SECRET` случайным значением из `openssl rand -hex 32`.

В HTTPS-конфиге SelfCall, в `location /`, где nginx уже проверяет Basic Auth и
проксирует сайт и API, добавьте две строки. Подставьте тот же секрет, что в `.env`.

```nginx
proxy_set_header X-SelfCall-Login $remote_user;
proxy_set_header X-SelfCall-Proxy-Secret "СЕКРЕТ_ИЗ_DIRECT_CALLS_PROXY_SECRET";
```

После проверки `nginx -t` перечитайте конфигурацию nginx и пересоберите SelfCall.
Список сотрудников хранится в томе Compose `direct-call-data`. При смене телефона
включите входящие на новом. При выключении входящих запись удаляется из списка.

Точный путь `/.well-known/assetlinks.json` доступен без пароля. Он нужен Android для проверки ссылок на встречи. Исключение задано в шаблоне сайта nginx и должно быть в HTTPS-блоке.

## Проверка

На сервере, подключившись по SSH:

```bash
cd ~/selfcall/infra
sudo docker compose ps
curl -fsS http://127.0.0.1:8110/api/health                 # {"status":"ok"}
```

С Mac:

```bash
curl -i https://call.gamma.help/api/health                # 401 без пароля
curl -fsSi https://call.gamma.help/.well-known/assetlinks.json  # 200, application/json
```

Проверь изменившиеся функции на тестовой встрече. API можно проверить с сервера через `127.0.0.1:8110`, а с Mac через HTTPS с `curl --user selfcall`, который запросит пароль. Если менялись звук, видео или подключение к встрече, проверь звонок из внешней сети. При изменении ссылок Android сравни ответ `assetlinks.json` с файлом в `frontend/public/.well-known/` и проверь открытие ссылки на телефоне при доступном устройстве.

## Обслуживание

На сервере, в `~/selfcall/infra`:

```bash
sudo docker compose ps
sudo docker compose logs -f livekit
sudo docker compose restart backend
sudo docker compose down            # остановить SelfCall, Gamma не затрагивается
```

Логи контейнеров ограничены: 3 файла по 10 МБ на контейнер.

## Необязательно: UDP-буферы

LiveKit советует увеличить буферы UDP. Это настройка ядра для всего сервера. Gamma от неё не пострадает, но делать её стоит осознанно:

```bash
printf 'net.core.rmem_max=5000000\nnet.core.wmem_max=5000000\n' | sudo tee /etc/sysctl.d/90-selfcall.conf
sudo sysctl --system
```

## Чего здесь нет и почему

- **TURN/TLS на 443.** Он помог бы в корпоративных сетях, где закрыто всё, кроме 443. Но для этого пришлось бы менять общий nginx (stream с разбором SNI) и затронуть Gamma. Пока хватает TCP 7881.
- **Правки брандмауэра сервера.** `hostfw` принадлежит Gamma, SelfCall его не меняет (см. пункт «Порты»).
