package com.example.cleancity.web

/**
 * Самодостаточные HTML-страницы для ссылок из писем (verify-email, reset-password,
 * accept-invite). Открываются в браузере на api.clean--city.ru — тот же origin, что и
 * JSON-эндпоинты /auth/ + *, поэтому CORS не нужен. Каждая страница инлайнит JS, который
 * POST'ит на существующий /auth/+ *-эндпоинт. Палитра — из EmailTemplates.
 *
 * Токен подставляется в JS-строку. Вызывающий код (WebAuthRoutes) ОБЯЗАН валидировать
 * токен по белому списку символов до подстановки, иначе reflected XSS.
 */
object HtmlPages {

    private const val GREEN = "#5DDE8A"
    private const val DARK = "#0d2b1a"

    // Defense-in-depth: WebAuthRoutes — первичный гейт. Здесь — вторичный: если будущий
    // вызыватель подставит непроверенный токен, бросаем, а не рендерим reflected XSS.
    private val SAFE_TOKEN = Regex("^[A-Za-z0-9._-]{1,512}$")
    private fun safeToken(token: String): String {
        require(SAFE_TOKEN.matches(token)) { "Unsafe token passed to HtmlPages" }
        return token
    }

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

    fun verifyEmail(token: String): String {
        val t = safeToken(token)
        return shell("Подтверждение email", """
        <p>Нажмите кнопку, чтобы подтвердить email и активировать аккаунт:</p>
        <button class="btn" id="go">Подтвердить email</button>
        <div class="msg" id="msg"></div>
        <p class="hint">Ссылка действительна 24 часа.</p>
        <script>
          document.getElementById('go').addEventListener('click', function(){
            var m = document.getElementById('msg'); this.disabled = true;
            fetch('/auth/verify-email', {method:'POST', headers:{'Content-Type':'application/json'},
              body: JSON.stringify({token: "$t"})})
              .then(function(r){
                if (r.ok) { m.className='msg ok'; m.textContent='Email подтверждён! Откройте приложение CleanCity и войдите.'; }
                else { m.className='msg err'; m.textContent='Ссылка недействительна или истекла. Запросите новое письмо в приложении.'; document.getElementById('go').disabled=false; }
              })
              .catch(function(){ m.className='msg err'; m.textContent='Ошибка сети. Попробуйте ещё раз.'; document.getElementById('go').disabled=false; });
          });
        </script>
        """.trimIndent())
    }

    fun resetPassword(token: String): String {
        val t = safeToken(token)
        return shell("Новый пароль", """
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
              body:JSON.stringify({token:"$t",newPassword:pw})})
              .then(function(r){ if(r.ok){m.className='msg ok';m.textContent='Пароль обновлён! Войдите заново.';}
                else{m.className='msg err';m.textContent='Ссылка недействительна или пароль слишком простой.';document.getElementById('go').disabled=false;} })
              .catch(function(){m.className='msg err';m.textContent='Ошибка сети.';document.getElementById('go').disabled=false;});
          });
        </script>
        """.trimIndent())
    }

    fun acceptInvite(token: String): String {
        val t = safeToken(token)
        return shell("Активация аккаунта", """
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
              body:JSON.stringify({token:"$t",password:pw})})
              .then(function(r){ if(r.ok){m.className='msg ok';m.textContent='Аккаунт активирован! Войдите в админ-кабинет.';}
                else{m.className='msg err';m.textContent='Ссылка недействительна или истекла.';document.getElementById('go').disabled=false;} })
              .catch(function(){m.className='msg err';m.textContent='Ошибка сети.';document.getElementById('go').disabled=false;});
          });
        </script>
        """.trimIndent())
    }

    fun error(): String = shell("Ошибка", """
        <p class="err">Ссылка повреждена или неполная. Запросите новое письмо в приложении.</p>
    """.trimIndent())
}
