package com.example.cleancity.email

/**
 * Абстракция отправки email.
 *
 * Реализации:
 *   - LoggingEmailService — для DEV. Печатает письмо в логи вместо отправки.
 *   - SmtpEmailService — для PROD. Реальная отправка через Yandex Mail SMTP.
 *
 * Текст шаблонов лежит в [EmailTemplates]. После пилота можно вынести в resources/templates/.
 */
interface EmailService {
    suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String? = null)
}

object EmailTemplates {

    fun verifyEmail(verifyLink: String): Pair<String, String> {
        val subject = "Подтвердите регистрацию в CleanCity"
        val html = """
            <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px">
            <h2 style="color:#0d2b1a">Чистый Город · Сочи</h2>
            <p>Спасибо за регистрацию! Подтвердите email, чтобы начать пользоваться сервисом:</p>
            <p><a href="$verifyLink" style="display:inline-block;background:#5DDE8A;color:#0d2b1a;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Подтвердить email</a></p>
            <p style="color:#666;font-size:13px">Или скопируйте ссылку: $verifyLink</p>
            <p style="color:#999;font-size:12px;margin-top:24px">Ссылка действительна 24 часа. Если вы не регистрировались — проигнорируйте письмо.</p>
            </body></html>
        """.trimIndent()
        return subject to html
    }

    fun resetPassword(resetLink: String): Pair<String, String> {
        val subject = "Восстановление пароля CleanCity"
        val html = """
            <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px">
            <h2 style="color:#0d2b1a">Чистый Город · Сочи</h2>
            <p>Получен запрос на восстановление пароля. Нажмите кнопку, чтобы установить новый:</p>
            <p><a href="$resetLink" style="display:inline-block;background:#5DDE8A;color:#0d2b1a;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Установить новый пароль</a></p>
            <p style="color:#666;font-size:13px">Или скопируйте ссылку: $resetLink</p>
            <p style="color:#999;font-size:12px;margin-top:24px">Ссылка действительна 1 час. Если вы не запрашивали — проигнорируйте письмо.</p>
            </body></html>
        """.trimIndent()
        return subject to html
    }

    fun adminInvite(acceptLink: String, invitedBy: String, recipientName: String): Pair<String, String> {
        val subject = "Вас пригласили в админ-кабинет CleanCity"
        val html = """
            <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px">
            <h2 style="color:#0d2b1a">Чистый Город · Сочи · Админ-кабинет</h2>
            <p>Здравствуйте, $recipientName!</p>
            <p>$invitedBy пригласил(а) вас в админ-кабинет CleanCity. Установите пароль, чтобы активировать аккаунт:</p>
            <p><a href="$acceptLink" style="display:inline-block;background:#5DDE8A;color:#0d2b1a;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Активировать аккаунт</a></p>
            <p style="color:#666;font-size:13px">Или скопируйте ссылку: $acceptLink</p>
            <p style="color:#999;font-size:12px;margin-top:24px">Ссылка действительна 7 дней.</p>
            </body></html>
        """.trimIndent()
        return subject to html
    }
}
