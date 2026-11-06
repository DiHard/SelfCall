# SelfCall — Frontend

React SPA (Vite + TypeScript + LiveKit Components).

## Экраны

| Путь | Экран |
|------|-------|
| `/` | Главная: «Создать встречу» и вход по ссылке или коду |
| `/m/:id` | Встреча: проверка ссылки → экран перед входом (превью камеры, проверка микрофона, выбор устройств, имя по желанию) → звонок → экран «Вы вышли» |

В звонке:
- сетка участников;
- при демонстрации экрана экран выводится крупно, камеры уходят в ленту;
- кнопки микрофона и камеры с выбором устройства;
- демонстрация экрана (в Chrome — со звуком вкладки);
- копирование ссылки, выход.

## Файлы

- `src/pages/HomePage.tsx`, `src/pages/MeetingPage.tsx` — экраны
- `src/components/PreJoin.tsx` — экран перед входом
- `src/components/CallRoom.tsx` — звонок
- `src/api.ts` — запросы к `/api`
- `src/lib.ts` — ссылки, настройки в localStorage, буфер обмена
- `src/index.css` — все стили и цветовые токены

## Локальная разработка

API вызывается по относительному пути `/api`. В `npm run dev` Vite проксирует его на бэкенд (`VITE_DEV_API_PROXY`, по умолчанию `http://localhost:8000`).

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm run lint
npm run build      # tsc -b && vite build → dist/
```

Для dev-режима нужны запущенные бэкенд и LiveKit. Проще всего поднять их через Docker из корневого [README](../README.md#локальный-запуск), а фронт запустить через `npm run dev`. Порт бэкенда в Docker наружу не открыт, поэтому в `.env` укажите `VITE_DEV_API_PROXY=http://localhost:8080`: Vite будет ходить в API через nginx контейнера.

## Docker

[Dockerfile](Dockerfile) собирает приложение и раздаёт его через nginx ([nginx.conf](nginx.conf)), а `/api/` проксирует в сервис `backend`.
