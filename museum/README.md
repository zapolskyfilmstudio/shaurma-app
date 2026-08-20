# Интернет-музей (интернетмузей.рф)

Отдельный фронтенд, не связанный с `client/` и `kitchen/`.

## Локально

```bash
cd museum
npm ci
npm run dev
```

## Деплой

Собирается в образ nginx вместе с остальными сайтами:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml build nginx
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx
```

Nginx отдаёт сайт только на `server_name` интернетмузей.рф (`xn--e1aaahcksfb3aueo.xn--p1ai`).

## Структура

- `museum/` — исходники сайта музея
- `infra/nginx.prod.conf` — блок `server` только для музея
- `infra/Dockerfile.nginx` — стадия `museum-build`
