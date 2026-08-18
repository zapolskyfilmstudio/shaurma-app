# Деплой на VPS

MVP запускается одинаково на ноутбуке и на VPS: меняется только `.env`.

## Подготовка

1. Установить Docker и Docker Compose plugin на Ubuntu.
2. Скопировать репозиторий на сервер.
3. Создать `.env` из `.env.example` и заменить:
   - `POSTGRES_PASSWORD`
   - `BEARER_TOKEN`
   - `CORS_ALLOWED_ORIGINS`
   - публичные URL для Android/Kitchen сборок.
   - `TBANK_TERMINAL_KEY`, `TBANK_PASSWORD`, `TBANK_NOTIFICATION_URL`, `TBANK_SUCCESS_URL`, `TBANK_FAIL_URL` для оплаты через T-Bank.

## T-Bank

1. В личном кабинете интернет-эквайринга возьмите **тестовый** терминал и пройдите тестовые платежи.
2. Укажите в `.env`:
   - `TBANK_TERMINAL_KEY`, `TBANK_PASSWORD`
   - `TBANK_API_URL=https://rest-api-test.tinkoff.ru/v2` для тестов или `https://securepay.tinkoff.ru/v2` для боевого терминала
   - `TBANK_NOTIFICATION_URL=https://<ваш-домен>/api/webhooks/tbank`
   - `TBANK_SUCCESS_URL=https://<ваш-домен>/?payment=success`
   - `TBANK_FAIL_URL=https://<ваш-домен>/?payment=fail`
3. В настройках терминала T-Bank включите HTTP(S)-уведомления или передавайте `NotificationURL` в Init (backend делает это автоматически).
4. После успешных тестов замените тестовый терминал на рабочий.

Если `TBANK_*` не заданы, заказы создаются без онлайн-оплаты (удобно для локальной разработки).

## Запуск

```bash
docker compose up --build -d
```

Логи backend:

```bash
docker compose logs -f backend
```

## Бэкап PostgreSQL

Пример ручного бэкапа:

```bash
docker compose exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql
```

Пример cron-задачи на хосте:

```cron
0 3 * * * cd /opt/shaurma-app && docker compose exec -T postgres pg_dump -U shaurma shaurma > /opt/backups/shaurma-$(date +\%F).sql
```

## HTTPS

`infra/nginx.conf` содержит пример reverse proxy. Сертификаты и DNS настраиваются отдельно под конкретный домен.

## Переключение сред

- Backend: только `.env`.
- Kitchen: пересобрать `kitchen/dist` с новым `VITE_API_URL`.
- Android: пересобрать APK с новым `BASE_URL`.
