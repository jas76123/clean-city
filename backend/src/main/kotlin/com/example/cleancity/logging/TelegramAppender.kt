package com.example.cleancity.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Логбэк-аппендер, который шлёт ERROR/WARN-события в Telegram через Bot API.
 *
 * Конфигурация — через env (читается лениво при первом событии):
 *   TELEGRAM_BOT_TOKEN  — токен бота от @BotFather
 *   TELEGRAM_CHAT_ID    — id чата/группы куда писать (отрицательный для групп)
 *
 * При отсутствии любого из них аппендер becomes no-op — не блокирует логирование.
 * HTTP-вызовы выполняются с 5-секундным таймаутом; ошибки не пробрасываются
 * (чтобы упавший Telegram не уронил приложение).
 *
 * Подключение — в logback.xml через `<appender ... class="com.example.cleancity.logging.TelegramAppender">`.
 */
class TelegramAppender : AppenderBase<ILoggingEvent>() {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    }
    private val token: String? by lazy { System.getenv("TELEGRAM_BOT_TOKEN")?.takeIf { it.isNotBlank() } }
    private val chatId: String? by lazy { System.getenv("TELEGRAM_CHAT_ID")?.takeIf { it.isNotBlank() } }

    // Простой rate-limit: не более 1 сообщения в секунду на appender,
    // чтобы шторм ERROR'ов не задудосил бота.
    @Volatile
    private var lastSentMs: Long = 0
    private val minIntervalMs = 1_000L

    override fun append(event: ILoggingEvent) {
        val t = token ?: return
        val c = chatId ?: return

        val now = System.currentTimeMillis()
        if (now - lastSentMs < minIntervalMs) return
        lastSentMs = now

        val text = buildString {
            append("[").append(event.level).append("] ")
            append(event.loggerName.substringAfterLast('.'))
            append("\n").append(event.formattedMessage)
            event.throwableProxy?.let {
                append("\n").append(it.className).append(": ").append(it.message ?: "")
            }
        }.take(4000)

        val body = "chat_id=" + URLEncoder.encode(c, StandardCharsets.UTF_8) +
            "&disable_web_page_preview=true" +
            "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)

        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot$t/sendMessage"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        runCatching {
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
        }
        // fire-and-forget — глотаем любые ошибки, чтобы не циклить аппендер сам на себя.
    }
}
