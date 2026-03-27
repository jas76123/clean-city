package com.example.cleancity.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class LocalStorageServiceTest {

    private fun createTempStorage(): LocalStorageService {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return LocalStorageService(tempDir.absolutePath, "http://localhost:8080")
    }

    @Test
    fun `save stores file and returns path`() {
        val storage = createTempStorage()
        val bytes = "fake-image-data".toByteArray()

        val path = storage.save("test.jpg", bytes)

        assertTrue(path.endsWith(".jpg"))
        val stored = storage.get(path)
        assertNotNull(stored)
        assertEquals("fake-image-data", String(stored))
    }

    @Test
    fun `get returns null for missing file`() {
        val storage = createTempStorage()

        val result = storage.get("nonexistent.jpg")

        assertNull(result)
    }

    @Test
    fun `getUrl returns full URL`() {
        val storage = createTempStorage()

        val url = storage.getUrl("abc123.jpg")

        assertEquals("http://localhost:8080/api/photos/abc123.jpg", url)
    }

    @Test
    fun `save generates unique filenames`() {
        val storage = createTempStorage()
        val bytes = "data".toByteArray()

        val path1 = storage.save("photo.jpg", bytes)
        val path2 = storage.save("photo.jpg", bytes)

        assertTrue(path1 != path2)
    }
}
