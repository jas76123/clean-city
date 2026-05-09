package com.example.cleancity.storage

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import org.imgscalr.Scalr
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * Декодирует входящие фото, чинит ориентацию из EXIF и пере-кодирует в JPEG.
 * Пере-кодирование автоматически удаляет EXIF — отдельная библиотека не нужна.
 *
 * `validate` проверяет magic bytes (а не mime, который клиент шлёт по своему усмотрению),
 * `normalizeOriginal` отдаёт исходник без EXIF, `thumbnail` — уменьшенную копию.
 */
object ImageProcessor {

    enum class ImageType(val mime: String, val ext: String) {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png")
    }

    private const val MAX_BYTES = 10L * 1024 * 1024 // 10 MB

    /** Бросает [IllegalArgumentException], если файл не картинка или > 10MB. */
    fun validate(bytes: ByteArray): ImageType {
        require(bytes.size <= MAX_BYTES) { "Photo exceeds 10MB limit" }
        return detectType(bytes) ?: throw IllegalArgumentException("Unsupported photo format (only JPEG/PNG)")
    }

    fun normalizeOriginal(bytes: ByteArray, quality: Float = 0.95f): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Failed to decode image")
        val rotated = applyExifOrientation(image, bytes)
        return encodeJpeg(rotated, quality)
    }

    fun thumbnail(bytes: ByteArray, maxSize: Int = 640, quality: Float = 0.85f): ByteArray {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Failed to decode image")
        val rotated = applyExifOrientation(image, bytes)
        val resized = Scalr.resize(rotated, Scalr.Method.QUALITY, maxSize)
        return encodeJpeg(resized, quality)
    }

    private fun detectType(bytes: ByteArray): ImageType? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> ImageType.JPEG
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> ImageType.PNG
        else -> null
    }

    private fun applyExifOrientation(image: BufferedImage, bytes: ByteArray): BufferedImage {
        val orientation = readOrientation(bytes) ?: return image
        return when (orientation) {
            3 -> rotate(image, 180.0)
            6 -> rotate(image, 90.0)
            8 -> rotate(image, 270.0)
            else -> image
        }
    }

    private fun readOrientation(bytes: ByteArray): Int? = try {
        val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(bytes))
        metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.takeIf { it.containsTag(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.getInt(ExifIFD0Directory.TAG_ORIENTATION)
    } catch (_: Exception) {
        null
    }

    private fun rotate(src: BufferedImage, degrees: Double): BufferedImage {
        val rad = Math.toRadians(degrees)
        val w = src.width
        val h = src.height
        val (newW, newH) = if (degrees == 90.0 || degrees == 270.0) h to w else w to h
        val out = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, newW, newH)
        g.translate((newW - w) / 2.0, (newH - h) / 2.0)
        g.rotate(rad, w / 2.0, h / 2.0)
        g.drawRenderedImage(src, null)
        g.dispose()
        return out
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) {
            image
        } else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { dst ->
                val g = dst.createGraphics()
                g.color = Color.WHITE
                g.fillRect(0, 0, image.width, image.height)
                g.drawImage(image, 0, 0, null)
                g.dispose()
            }
        }
        val baos = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(baos).use { ios ->
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.output = ios
            writer.write(null, IIOImage(rgb, null, null), params)
            writer.dispose()
        }
        return baos.toByteArray()
    }
}
