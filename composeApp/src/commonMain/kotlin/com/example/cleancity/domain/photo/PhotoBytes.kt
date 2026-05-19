package com.example.cleancity.domain.photo

/**
 * Содержимое выбранного фото в байтах + имя файла. PhotoPicker возвращает список таких,
 * ComplaintsApi.create отправляет их в multipart части `photo`.
 */
data class PhotoBytes(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoBytes) return false
        return filename == other.filename &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
