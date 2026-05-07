package com.example.cleancity.email

import jakarta.mail.Address
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * Production-реализация. Использует Yandex Mail SMTP (или любой совместимый).
 * Все настройки — через ENV (см. .env.example).
 *
 * SSL/TLS на 465 (рекомендуется) или STARTTLS на 587.
 * Аутентификация — по паролю приложения, НЕ обычным паролем почты.
 */
class SmtpEmailService(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val from: String
) : EmailService {

    init {
        require(host.isNotBlank()) { "SMTP_HOST is required" }
        require(username.isNotBlank()) { "SMTP_USER is required" }
        require(password.isNotBlank()) { "SMTP_PASSWORD is required" }
        require(from.isNotBlank()) { "EMAIL_FROM is required" }
    }

    private val session: Session = run {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        })
    }

    override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) {
        withContext(Dispatchers.IO) {
            val msg = MimeMessage(session)
            val fromAddress: Address = InternetAddress(from)
            val toAddress: Address = InternetAddress(to)
            msg.setFrom(fromAddress)
            msg.addRecipient(Message.RecipientType.TO, toAddress)
            msg.setSubject(subject, "UTF-8")
            msg.setContent(htmlBody, "text/html; charset=UTF-8")
            Transport.send(msg)
        }
    }
}
