package com.example.cleancity.auth

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * TOTP (RFC 6238). Совместим с Google Authenticator / Яндекс.Ключ:
 * SHA-1, 6 цифр, шаг 30 секунд. На проверке коде допускаем окно ±1 шаг
 * (компенсация рассинхрона часов телефона).
 */
class TotpService(
    private val issuer: String = "CleanCity",
    private val secretBytes: Int = 20,
    private val random: SecureRandom = SecureRandom()
) {
    private val base32 = Base32()
    private val config = TimeBasedOneTimePasswordConfig(
        codeDigits = 6,
        hmacAlgorithm = HmacAlgorithm.SHA1,
        timeStep = 30,
        timeStepUnit = TimeUnit.SECONDS
    )

    fun generateSecretBase32(): String {
        val raw = ByteArray(secretBytes).also { random.nextBytes(it) }
        return base32.encodeAsString(raw).trimEnd('=')
    }

    fun otpAuthUri(secretBase32: String, accountName: String): String {
        // RFC: label = issuer ":" account-name; двоеточие оставляем некодированным.
        val label = URLEncoder.encode("$issuer:$accountName", StandardCharsets.UTF_8)
            .replace("%3A", ":")
        val issuerEnc = URLEncoder.encode(issuer, StandardCharsets.UTF_8)
        return "otpauth://totp/$label?secret=$secretBase32&issuer=$issuerEnc" +
            "&algorithm=SHA1&digits=6&period=30"
    }

    fun verify(secretBase32: String, code: String, now: Long = System.currentTimeMillis()): Boolean {
        val secret = decode(secretBase32) ?: return false
        val gen = TimeBasedOneTimePasswordGenerator(secret, config)
        val stepMs = config.timeStepUnit.toMillis(config.timeStep)
        // ±1 шаг
        return (-1..1).any { offset ->
            gen.generate(now + offset * stepMs) == code
        }
    }

    /**
     * Генерация текущего TOTP-кода для секрета. Используется в тестах
     * и при превью админом — например, чтобы проверить, что секрет настроен корректно.
     */
    fun generateCode(secretBase32: String, now: Long = System.currentTimeMillis()): String {
        val secret = decode(secretBase32) ?: error("Invalid base32 secret")
        return TimeBasedOneTimePasswordGenerator(secret, config).generate(now)
    }

    private fun decode(secretBase32: String): ByteArray? = try {
        // Base32 в otpauth обычно без паддинга — добавим, если нужно.
        val padded = secretBase32.padEnd((secretBase32.length + 7) / 8 * 8, '=')
        base32.decode(padded)
    } catch (_: Exception) {
        null
    }
}
