# Управление секретами в CleanCity

> Никогда не коммить пароли, токены, ключи или service-account JSON в git.

## Что считается секретом

| Тип | Где используется | Примеры |
|-----|------------------|---------|
| Пароль БД | Postgres | `DB_PASSWORD`, `POSTGRES_PASSWORD` |
| JWT-секрет | подпись токенов | `JWT_SECRET` |
| S3 ключи | Yandex Object Storage | `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` |
| Yandex Maps API key | мобильный клиент | `YANDEX_MAPS_API_KEY` |
| SMTP-пароль | email-рассылки | `SMTP_PASSWORD` |
| FCM service-account | push-уведомления | `secrets/fcm.json` |
| Пароль первого админа | seed-миграция | `INITIAL_ADMIN_PASSWORD` |

## Куда они сохраняются

### Локальная разработка

| Файл | Содержимое | В git? |
|------|------------|--------|
| `.env` | Все ENV-переменные с реальными dev-значениями | ❌ нет |
| `.env.example` | Шаблон БЕЗ значений с пояснениями | ✅ да |
| `local.properties` | Android SDK путь + `YANDEX_MAPS_API_KEY` | ❌ нет |
| `secrets/fcm.json` | Firebase service-account JSON | ❌ нет |

### Production (Yandex Cloud)

- **Yandex Lockbox** — managed-секреты, инжектятся в ВМ при старте.
- **Никогда** не оставляй секреты в `docker-compose.prod.yml` или `Dockerfile`.
- Переменные окружения передаются через `env_file` или `--env-file`.
- Доступ к Lockbox — только через service-аккаунт с минимальными правами.

## Как настроить локально

```bash
# 1. Скопируй шаблон
cp .env.example .env

# 2. Сгенерируй сильные пароли
openssl rand -base64 48 > /tmp/jwt-secret
# Затем впиши в .env как JWT_SECRET

# 3. Получи Yandex Maps API ключ
#    https://developer.tech.yandex.ru → MapKit Mobile SDK → Получить ключ
#    Привяжи к package name `com.example.cleancity`
#    Запиши в local.properties как YANDEX_MAPS_API_KEY=...

# 4. Запусти
docker compose up
```

## Что делать при утечке

1. **Немедленно ротируй ключ** в кабинете провайдера (Yandex Cloud, Firebase, SMTP-провайдер).
2. **Отзови JWT-сессии:** `UPDATE refresh_tokens SET revoked_at = NOW();`
3. **Сменить `JWT_SECRET`** → все access-токены инвалидируются автоматически.
4. **Проверь `audit_log`** на подозрительную активность.
5. **Перепиши git-историю** через `git filter-repo` или BFG Repo-Cleaner (если секрет уже в публичном репо).
6. **Уведоми пользователей** при компрометации PII.

## Что НЕЛЬЗЯ делать

- ❌ `git add .env`
- ❌ Хардкодить ключи в исходниках (`MapKitFactory.setApiKey("abc...")`).
- ❌ Логировать пароли, токены, OTP-коды.
- ❌ Передавать секреты через query string (`?token=...`) — только в headers/body.
- ❌ Сохранять секреты в Slack, Telegram, в комментариях к PR.
- ❌ Переиспользовать prod-секреты в dev/staging.

## Защитная сетка

- `.gitignore` исключает `.env`, `secrets/`, `*.pem`, `*.key`, `*-key.json`, `fcm.json`, `google-services.json`.
- `application.conf` использует `${?VAR}` — fallback на ENV, не дефолтные секреты.
- `DatabaseConfig.kt` — fail-fast: бросает исключение если `DB_PASSWORD` пустой или совпадает с insecure-списком (`cleancity`, `password`, `admin`, и т.д.) при `APP_STAGE != DEV`.
- Перед коммитом: `git diff --cached | grep -iE "(password|secret|key)\\s*[=:]"` — поможет заметить случайно попавшие секреты.
