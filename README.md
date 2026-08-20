# shaurma-app

Монорепозиторий MVP системы заказов для небольшой шаурмичной:

- `android/` — клиентское Android-приложение на Kotlin + Jetpack Compose.
- `backend/` — REST API на Kotlin + Ktor + Exposed + PostgreSQL.
- `kitchen/` — рабочая станция повара на React + TypeScript + Vite.
- `client/` — клиентский сайт заказов на React + TypeScript + Vite.
- `docs/api/openapi.yaml` — общий API-контракт.
- `seeds/init_menu.sql` — демо-меню для первичного запуска.
- `docker-compose.yml` — локальный/боевой compose с конфигурацией через `.env`.

## Быстрый старт

1. Скопируйте переменные окружения:

   ```bash
   cp .env.example .env
   ```

2. Заполните значения в `.env`.

3. Запустите инфраструктуру:

   ```bash
   docker compose up --build
   ```

Подробности переноса на VPS описаны в `docs/deploy.md`.

## Оплата T-Bank

При заполненных `TBANK_TERMINAL_KEY` и `TBANK_PASSWORD` заказ создаётся со статусом `WAITING`, клиент перенаправляется на платёжную форму T-Bank, а после webhook `CONFIRMED` заказ попадает на кухню. Если переменные пустые, заказ сразу считается оплаченным (режим разработки).
