package com.example.cleancity.email

import org.slf4j.LoggerFactory

/**
 * DEV-реализация: печатает письмо в лог вместо отправки.
 * Удобно для проверки flow регистрации/сброса без поднятого SMTP.
 */
class LoggingEmailService : EmailService {

    private val log = LoggerFactory.getLogger(LoggingEmailService::class.java)

    override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) {
        log.info(
            "\n========== [DEV EMAIL — NOT SENT] ==========\n" +
                "To: $to\n" +
                "Subject: $subject\n" +
                "Body:\n$htmlBody\n" +
                "============================================"
        )
    }
}
