# Day-18 Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 404 on email links (verify-email / reset-password / accept-invite), turn on real SMTP via Yandex 360, and prove the encrypted backup can be restored.

**Architecture:** Add a thin server-rendered HTML layer (`webAuthRoutes`) on the backend that serves branded pages at the exact paths the email links already point to (`api.clean--city.ru/<path>?token=`). Each page's inline JS POSTs to the existing JSON `/auth/*` endpoint on the same origin (no CORS). SMTP is pure config (code is already wired). Backup restore is a new ops script that restores into an ephemeral Postgres container, never prod.

**Tech Stack:** Ktor (Kotlin), Exposed, H2 (tests), Docker Compose, Caddy, Yandex 360 SMTP, Yandex Object Storage (S3), gpg, pg_restore.

**Critical ordering:** Task 1 (code) must be built + deployed **before** Task 5 (SMTP env) — otherwise real emails ship links that still 404. Task 6 (restore test) is independent.

**Workflow:** commits go straight to `main` (no feature branch), per project convention.

---

### Task 1: HTML pages for email links (`HtmlPages.kt`)

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/web/HtmlPages.kt`

Pure functions returning self-contained HTML strings. No server-side service calls — the page's JS calls the existing `/auth/*` endpoints. Tokens are reflected into the page, so callers MUST pass only validated tokens (Task 2 enforces this).

- [ ] **Step 1: Create `HtmlPages.kt`**

```kotlin
package com.example.cleancity.web

/**
 * Самодостаточные HTML-страницы для ссылок из писем (verify-email, reset-password,
 * accept-invite). Открываются в браузере на api.clean--city.ru — тот же origin, что и
 * JSON-эндпоинты /auth/*, поэтому CORS не нужен. Каждая страница инлайнит JS, который
 * POST'ит на существующий /auth/*-эндпоинт. Палитра — из EmailTemplates.
 *
 * Токен подставляется в JS-строку. Вызывающий код (WebAuthRoutes) ОБЯЗАН валидировать
 * токен по белому списку символов до подстановки, иначе reflected XSS.
 */
object HtmlPages {

    private const val GREEN = "#5DDE8A"
    private const val DARK = "#0d2b1a"

    private fun shell(title: String, inner: String): String = """
        <!doctype html><html lang="ru"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title · CleanCity</title>
        <style>
          body{font-family:sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:$DARK}
          h2{color:$DARK}
          button,input{font-size:16px}
          input{width:100%;box-sizing:border-box;padding:12px;margin:8px 0;border:1px solid #ccc;border-radius:8px}
          .btn{display:inline-block;background:$GREEN;color:$DARK;padding:12px 24px;border:0;border-radius:8px;font-weight:600;cursor:pointer}
          .msg{margin-top:16px;font-size:15px}
          .err{color:#b00020}
          .ok{color:#0a7d33}
          .hint{color:#999;font-size:13px;margin-top:24px}
        </style></head><body>
        <h2>Чистый Город · Сочи</h2>
        $inner
        </body></html>
    """.trimIndent()

    fun verifyEmail(token: String): String = shell("Подтверждение email", """
        <p>Нажмите кнопку, чтобы подтвердить email и активировать аккаунт:</p>
        <button class="btn" id="go">Подтвердить email</button>
        <div class="msg" id="msg"></div>
        <p class="hint">Ссылка действительна 24 часа.</p>
        <script>
          document.getElementById('go').addEventListener('click', function(){
            var m = document.getElementById('msg'); this.disabled = true;
            fetch('/auth/verify-email', {method:'POST', headers:{'Content-Type':'application/json'},
              body: JSON.stringify({token: "$token"})})
              .then(function(r){
                if (r.ok) { m.className='msg ok'; m.textContent='Email подтверждён! Откройте приложение CleanCity и войдите.'; }
                else { m.className='msg err'; m.textContent='Ссылка недействительна или истекла. Запросите новое письмо в приложении.'; document.getElementById('go').disabled=false; }
              })
              .catch(function(){ m.className='msg err'; m.textContent='Ошибка сети. Попробуйте ещё раз.'; document.getElementById('go').disabled=false; });
          });
        </script>
    """.trimIndent())

    fun resetPassword(token: String): String = shell("Новый пароль", """
        <p>Введите новый пароль:</p>
        <input type="password" id="pw" placeholder="Новый пароль" autocomplete="new-password">
        <input type="password" id="pw2" placeholder="Повторите пароль" autocomplete="new-password">
        <button class="btn" id="go">Сохранить пароль</button>
        <div class="msg" id="msg"></div>
        <script>
          document.getElementById('go').addEventListener('click', function(){
            var pw=document.getElementById('pw').value, pw2=document.getElementById('pw2').value;
            var m=document.getElementById('msg');
            if(pw.length<8){m.className='msg err';m.textContent='Пароль не короче 8 символов.';return;}
            if(pw!==pw2){m.className='msg err';m.textContent='Пароли не совпадают.';return;}
            this.disabled=true;
            fetch('/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},
              body:JSON.stringify({token:"$token",newPassword:pw})})
              .then(function(r){ if(r.ok){m.className='msg ok';m.textContent='Пароль обновлён! Войдите заново.';}
                else{m.className='msg err';m.textContent='Ссылка недействительна или пароль слишком простой.';document.getElementById('go').disabled=false;} })
              .catch(function(){m.className='msg err';m.textContent='Ошибка сети.';document.getElementById('go').disabled=false;});
          });
        </script>
    """.trimIndent())

    fun acceptInvite(token: String): String = shell("Активация аккаунта", """
        <p>Установите пароль, чтобы активировать аккаунт сотрудника:</p>
        <input type="password" id="pw" placeholder="Пароль" autocomplete="new-password">
        <input type="password" id="pw2" placeholder="Повторите пароль" autocomplete="new-password">
        <button class="btn" id="go">Активировать аккаунт</button>
        <div class="msg" id="msg"></div>
        <script>
          document.getElementById('go').addEventListener('click', function(){
            var pw=document.getElementById('pw').value, pw2=document.getElementById('pw2').value;
            var m=document.getElementById('msg');
            if(pw.length<8){m.className='msg err';m.textContent='Пароль не короче 8 символов.';return;}
            if(pw!==pw2){m.className='msg err';m.textContent='Пароли не совпадают.';return;}
            this.disabled=true;
            fetch('/auth/admin/accept-invite',{method:'POST',headers:{'Content-Type':'application/json'},
              body:JSON.stringify({token:"$token",password:pw})})
              .then(function(r){ if(r.ok){m.className='msg ok';m.textContent='Аккаунт активирован! Войдите в админ-кабинет.';}
                else{m.className='msg err';m.textContent='Ссылка недействительна или истекла.';document.getElementById('go').disabled=false;} })
              .catch(function(){m.className='msg err';m.textContent='Ошибка сети.';document.getElementById('go').disabled=false;});
          });
        </script>
    """.trimIndent())

    fun error(): String = shell("Ошибка", """
        <p class="err">Ссылка повреждена или неполная. Запросите новое письмо в приложении.</p>
    """.trimIndent())
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :backend:compileKotlin -q`
Expected: BUILD SUCCESSFUL (no test yet — `HtmlPages` is exercised via Task 2's route tests).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/web/HtmlPages.kt
git commit -m "feat(web): HTML pages for email-link landing (verify/reset/invite)"
```

---

### Task 2: Web auth routes + registration (`WebAuthRoutes.kt`)

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/web/WebAuthRoutes.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/web/WebAuthRoutesTest.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt` (routing block ~line 162-185)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/example/cleancity/web/WebAuthRoutesTest.kt`:

```kotlin
package com.example.cleancity.web

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebAuthRoutesTest {

    private fun app(block: suspend (HttpClient) -> Unit) = testApplication {
        application { routing { webAuthRoutes() } }
        block(client)
    }

    @Test
    fun `verify-email page renders token and posts to auth endpoint`() = app { client ->
        val r = client.get("/verify-email?token=abc123def")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("abc123def"), "token must be embedded")
        assertTrue(body.contains("/auth/verify-email"), "must POST to verify endpoint")
        assertTrue(body.contains("Подтвердить email"))
    }

    @Test
    fun `reset-password page renders form posting to reset endpoint`() = app { client ->
        val r = client.get("/reset-password?token=tok123")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("/auth/reset-password"))
        assertTrue(body.contains("tok123"))
    }

    @Test
    fun `accept-invite page renders form posting to invite endpoint`() = app { client ->
        val r = client.get("/accept-invite?token=inv999")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("/auth/admin/accept-invite"))
        assertTrue(body.contains("inv999"))
    }

    @Test
    fun `missing token returns 400 error page`() = app { client ->
        val r = client.get("/verify-email")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("повреждена"))
    }

    @Test
    fun `malformed token returns 400`() = app { client ->
        val r = client.get("/verify-email?token=%3Cscript%3E")
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :backend:test --tests "com.example.cleancity.web.WebAuthRoutesTest"`
Expected: FAIL — compilation error, `webAuthRoutes` is unresolved.

- [ ] **Step 3: Create `WebAuthRoutes.kt`**

```kotlin
package com.example.cleancity.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Токены из createEmailToken — hex; допускаем безопасный белый список символов.
private val TOKEN_RE = Regex("^[A-Za-z0-9._-]{1,512}$")

/**
 * HTML-страницы для ссылок из писем. Регистрируются в КОРНЕ routing (НЕ под /auth),
 * т.к. AuthService строит ссылки как `$baseUrl/verify-email`, `/reset-password`,
 * `/accept-invite`. Сами мутации делает JS страницы через существующие /auth/*-POST.
 */
fun Route.webAuthRoutes() {
    get("/verify-email") { call.respondAuthPage(HtmlPages::verifyEmail) }
    get("/reset-password") { call.respondAuthPage(HtmlPages::resetPassword) }
    get("/accept-invite") { call.respondAuthPage(HtmlPages::acceptInvite) }
}

private suspend fun ApplicationCall.respondAuthPage(render: (String) -> String) {
    val token = request.queryParameters["token"]
    if (token == null || !TOKEN_RE.matches(token)) {
        respondText(HtmlPages.error(), ContentType.Text.Html, HttpStatusCode.BadRequest)
        return
    }
    respondText(render(token), ContentType.Text.Html, HttpStatusCode.OK)
}
```

- [ ] **Step 4: Register the routes in `Application.kt`**

Add the import near the other route imports (next to `import com.example.cleancity.auth.authRoutes` at line 11):

```kotlin
import com.example.cleancity.web.webAuthRoutes
```

In the `routing { … }` block (right after `legalRoutes()` at ~line 176), add:

```kotlin
        webAuthRoutes()
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :backend:test --tests "com.example.cleancity.web.WebAuthRoutesTest"`
Expected: PASS (5 tests green).

- [ ] **Step 6: Run the full backend test suite (no regressions)**

Run: `./gradlew :backend:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/web/WebAuthRoutes.kt \
        backend/src/test/kotlin/com/example/cleancity/web/WebAuthRoutesTest.kt \
        backend/src/main/kotlin/com/example/cleancity/Application.kt
git commit -m "feat(web): serve verify/reset/invite landing pages, fix email-link 404"
```

---

### Task 3: Build + deploy the backend to prod

**Files:** none (deployment). Runs on the body VM (`89.169.131.29`, repo at `/opt/cleancity/repo`).

- [ ] **Step 1: Push `main`**

```bash
git push origin main
```
Expected: 12+ commits pushed (the repo was 10 ahead before this work).

- [ ] **Step 2: SSH to prod and pull**

```bash
ssh cleancity-prod   # host alias from ~/.ssh/config → 89.169.131.29
cd /opt/cleancity/repo && git pull origin main
```
Expected: fast-forward to the latest commit.

- [ ] **Step 3: Rebuild + restart the backend image (code changed)**

```bash
cd /opt/cleancity/repo/deploy
docker compose --env-file /opt/cleancity/.env.prod -f docker-compose.prod.yml build backend
docker compose --env-file /opt/cleancity/.env.prod -f docker-compose.prod.yml up -d backend
```
Expected: backend container recreated. (A code change requires `build`; `up -d` alone would keep the old image — see project note "Docker backend rebuild после коммита".)

- [ ] **Step 4: Verify the pages resolve (no 404)**

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://api.clean--city.ru/verify-email?token=test123"
```
Expected: `200`. Then `curl -s "https://api.clean--city.ru/verify-email?token=test123" | grep -c "Подтвердить email"` → `1`.
Also check `curl ... "/verify-email"` (no token) → `400`.

---

### Task 4: (Your part) Free Yandex mailbox + app-password

**Decision (2026-05-29):** Жасмин chose a **free personal `@yandex.ru` mailbox** over Yandex 360 для бизнеса — no subscription, no domain mail, no DNS records (MX/SPF/DKIM) and no domain verification. Trade-off: the sender address is `<account>@yandex.ru`, not `noreply@clean--city.ru`. Verification links are unaffected — they point to `api.clean--city.ru` (Task 3). Yandex SMTP rejects a `From` that differs from the authenticated account, so `EMAIL_FROM`'s address must equal `SMTP_USER`.

Owner: Жасмин. No code, no DNS.

- [ ] **Step 1:** Create a free Yandex mailbox at https://mail.yandex.ru/ (e.g. `cleancity.sochi@yandex.ru`).
- [ ] **Step 2:** Enable protocol access: Mail → Настройки → «Почтовые программы» (https://mail.yandex.ru/#setup/client) → allow access via mail clients (IMAP/SMTP). Save.
- [ ] **Step 3:** Create an **app-password** at https://id.yandex.ru/security/app-passwords → type «Почта». Copy the 16-char password (shown once).
- [ ] **Step 4:** Send Claude in-session: the `@yandex.ru` address + the app-password. It goes only into `.env.prod`, never into git/commits.

---

### Task 5: (Server part) Turn on SMTP

**Files:** `/opt/cleancity/.env.prod` on prod (NOT in git, mode 0600). No code — `buildEmailService()` auto-switches to `SmtpEmailService` once `SMTP_HOST`+`SMTP_USER` are set, and `docker-compose.prod.yml` already passes the `SMTP_*` env into the backend container.

**Precondition:** Task 3 deployed (links resolve) AND Task 4 done (app-password in hand).

- [ ] **Step 1: Edit `/opt/cleancity/.env.prod`** (on prod, over SSH) — set:

```bash
EMAIL_FROM=CleanCity Сочи <<account>@yandex.ru>
SMTP_HOST=smtp.yandex.ru
SMTP_PORT=465
SMTP_USER=<account>@yandex.ru
SMTP_PASSWORD=<app-password-from-task-4>
```
Keep file mode `0600`. The `EMAIL_FROM` address must equal `SMTP_USER` (Yandex rejects a mismatched `From`); only the display name «CleanCity Сочи» is free-form.

- [ ] **Step 2: Recreate the backend container so it picks up new env**

```bash
cd /opt/cleancity/repo/deploy
docker compose --env-file /opt/cleancity/.env.prod -f docker-compose.prod.yml up -d backend
```
Expected: container recreated (env-only change — no rebuild needed).

- [ ] **Step 3: Confirm SMTP is active in logs**

```bash
docker compose -f docker-compose.prod.yml logs --tail=50 backend | grep "EmailService"
```
Expected: `EmailService: using SmtpEmailService (host=smtp.yandex.ru:465, from=...)`.

- [ ] **Step 4: End-to-end real email**

Trigger resend to a real inbox you control:

```bash
curl -s -X POST https://api.clean--city.ru/auth/resend-verification \
  -H "Content-Type: application/json" -d '{"email":"<your-test-inbox>"}'
```
Expected: the verification email arrives, and clicking its link opens the verify-email page and confirms successfully (relies on Task 3). If the email lands in spam, re-check SPF/DKIM (Task 4 step 2).

---

### Task 6: Backup restore test (`ops/restore-test.sh`)

**Files:**
- Create: `ops/restore-test.sh`

Restores a backup into an **ephemeral Postgres container** — never prod. Run on the prod VM (where S3 creds + gpg passphrase live).

- [ ] **Step 1: Create `ops/restore-test.sh`**

```bash
#!/usr/bin/env bash
# Тест восстановления бэкапа: S3 → gpg --decrypt → pg_restore в ЭФЕМЕРНЫЙ контейнер.
# НИКОГДА не трогает прод-БД. Запуск на боевой VM (есть доступ к S3 и passphrase-файлу):
#   sudo BACKUP_CONFIG=/etc/cleancity/backup.env ./restore-test.sh [S3_KEY]
# Без S3_KEY берётся самый свежий объект из postgres/.
set -euo pipefail

CONFIG_FILE="${BACKUP_CONFIG:-/etc/cleancity/backup.env}"
[[ -r "$CONFIG_FILE" ]] || { echo "FATAL: config not readable: $CONFIG_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PGDATABASE:?PGDATABASE is required}"
: "${GPG_PASSPHRASE_FILE:?GPG_PASSPHRASE_FILE is required}"
: "${S3_BUCKET:?S3_BUCKET is required}"
: "${S3_ENDPOINT:=https://storage.yandexcloud.net}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is required}"
: "${AWS_DEFAULT_REGION:=ru-central1}"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_DEFAULT_REGION

SCRATCH_DB="cleancity_restore_test"
# Guard: scratch-БД не должна совпадать с прод-БД.
if [[ "$SCRATCH_DB" == "$PGDATABASE" ]]; then
  echo "FATAL: scratch DB name equals prod DB ($PGDATABASE) — aborting" >&2; exit 1
fi
[[ ! -r "$GPG_PASSPHRASE_FILE" ]] && { echo "FATAL: passphrase not readable" >&2; exit 1; }

# 1) Выбрать ключ объекта (последний, если не задан явно).
S3_KEY="${1:-}"
if [[ -z "$S3_KEY" ]]; then
  S3_KEY="$(aws --endpoint-url="$S3_ENDPOINT" s3 ls "s3://$S3_BUCKET/postgres/" --recursive \
            | sort | tail -n1 | awk '{print $4}')"
  [[ -n "$S3_KEY" ]] || { echo "FATAL: no backups in s3://$S3_BUCKET/postgres/" >&2; exit 1; }
fi
echo "[restore-test] using s3://$S3_BUCKET/$S3_KEY"

TMPDIR="$(mktemp -d -t cleancity-restore-XXXXXX)"
CONTAINER="cleancity-restore-pg-$$"
cleanup(){ docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; rm -rf "$TMPDIR"; }
trap cleanup EXIT

ENC="$TMPDIR/backup.dump.gpg"; DUMP="$TMPDIR/backup.dump"

# 2) Скачать + расшифровать.
aws --endpoint-url="$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$S3_KEY" "$ENC" --no-progress
gpg --batch --quiet --passphrase-file "$GPG_PASSPHRASE_FILE" --decrypt "$ENC" > "$DUMP"
DUMP_SIZE=$(stat -c %s "$DUMP" 2>/dev/null || stat -f %z "$DUMP")
echo "[restore-test] decrypted $DUMP_SIZE bytes"
(( DUMP_SIZE > 1024 )) || { echo "FATAL: decrypted dump suspiciously small" >&2; exit 2; }

# 3) Эфемерный Postgres (тот же образ, что в проде).
docker run -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD=restore -e POSTGRES_DB="$SCRATCH_DB" \
  postgis/postgis:16-3.4 >/dev/null
for _ in $(seq 1 30); do
  docker exec "$CONTAINER" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done

# 4) pg_restore внутри контейнера.
docker cp "$DUMP" "$CONTAINER:/tmp/backup.dump"
docker exec "$CONTAINER" pg_restore --no-owner --no-privileges \
  -U postgres -d "$SCRATCH_DB" /tmp/backup.dump

# 5) Проверка: ключевые таблицы есть и число строк — целое.
ROWS_USERS=$(docker exec "$CONTAINER" psql -U postgres -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM users")
ROWS_COMPLAINTS=$(docker exec "$CONTAINER" psql -U postgres -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM complaints")
ROWS_USERS="${ROWS_USERS//[[:space:]]/}"; ROWS_COMPLAINTS="${ROWS_COMPLAINTS//[[:space:]]/}"
echo "[restore-test] users=$ROWS_USERS complaints=$ROWS_COMPLAINTS"

if [[ "$ROWS_USERS" =~ ^[0-9]+$ && "$ROWS_COMPLAINTS" =~ ^[0-9]+$ ]]; then
  echo "[restore-test] PASS"
else
  echo "[restore-test] FAIL: row counts not numeric" >&2; exit 2
fi
```

- [ ] **Step 2: Make it executable + commit**

```bash
chmod +x ops/restore-test.sh
git add ops/restore-test.sh
git commit -m "ops: backup restore-test script (S3 → gpg → pg_restore into scratch container)"
git push origin main
```

- [ ] **Step 3: Run it on prod**

```bash
ssh cleancity-prod
cd /opt/cleancity/repo && git pull origin main
sudo BACKUP_CONFIG=/etc/cleancity/backup.env ops/restore-test.sh
```
Expected: ends with `[restore-test] PASS` and sane row counts. The scratch container and temp files are auto-removed on exit; prod DB untouched.

---

## Self-Review

**Spec coverage:**
- Unit 1 (verify-email + all three links) → Tasks 1, 2, 3. ✓
- Unit 2 (SMTP, split by ownership) → Task 4 (Жасмин: Yandex/REG.RU) + Task 5 (server). ✓
- Unit 3 (backup restore) → Task 6. ✓
- Critical ordering (code before SMTP env) → encoded: Task 3 deploy precedes Task 5; Task 5 lists Task 3 as precondition. ✓

**Placeholder scan:** `<your-test-inbox>` and `<app-password-from-task-4>` are intentional user-supplied secrets/inputs, not implementation gaps. No TBD/TODO in code steps.

**Type consistency:** `webAuthRoutes()` (no args) defined in Task 2 matches the registration call in Task 2 Step 4. `HtmlPages.verifyEmail/resetPassword/acceptInvite/error` defined in Task 1 match the references in Task 2's `WebAuthRoutes.kt`. POST targets in the JS (`/auth/verify-email`, `/auth/reset-password`, `/auth/admin/accept-invite`) match existing routes in `AuthRoutes.kt`. Request body shapes match `VerifyEmailRequest{token}`, `ResetPasswordRequest{token,newPassword}`, `AcceptInviteRequest{token,password}`. Table names `users`/`complaints` match the Exposed `Table("…")` definitions.
