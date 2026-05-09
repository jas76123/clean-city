package com.example.cleancity.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStorageServiceTest {

    private fun createTempStorage(): Pair<LocalStorageService, File> {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.nanoTime()}")
        tempDir.mkdirs()
        return LocalStorageService(tempDir.absolutePath, "http://localhost:8080") to tempDir
    }

    @Test
    fun `save writes file and returns public URL`() {
        val (storage, _) = createTempStorage()
        val bytes = "fake-image".toByteArray()

        val url = storage.save("photos/2026/05/abc.jpg", bytes, "image/jpeg")

        assertEquals("http://localhost:8080/photos/photos/2026/05/abc.jpg", url)
        val stored = storage.read("photos/2026/05/abc.jpg")
        assertNotNull(stored)
        assertEquals("fake-image", String(stored))
    }

    @Test
    fun `read returns null for missing key`() {
        val (storage, _) = createTempStorage()
        assertNull(storage.read("photos/2026/05/missing.jpg"))
    }

    @Test
    fun `read rejects path traversal`() {
        val (storage, _) = createTempStorage()
        assertNull(storage.read("../etc/passwd"))
        assertNull(storage.read("/absolute/path"))
    }

    @Test
    fun `delete removes file and is idempotent`() {
        val (storage, _) = createTempStorage()
        storage.save("photos/2026/05/x.jpg", "abc".toByteArray(), "image/jpeg")
        assertNotNull(storage.read("photos/2026/05/x.jpg"))

        storage.delete("photos/2026/05/x.jpg")
        assertNull(storage.read("photos/2026/05/x.jpg"))

        // повторный delete не падает
        storage.delete("photos/2026/05/x.jpg")
    }

    @Test
    fun `urlFor returns predictable URL`() {
        val (storage, _) = createTempStorage()
        assertEquals("http://localhost:8080/photos/photos/2026/05/abc.jpg", storage.urlFor("photos/2026/05/abc.jpg"))
    }

    @Test
    fun `save creates nested directories`() {
        val (storage, root) = createTempStorage()
        storage.save("photos/2026/05/deep.jpg", "x".toByteArray(), "image/jpeg")
        assertTrue(File(root, "photos/2026/05/deep.jpg").exists())
        assertFalse(File(root, "photos/2026/06").exists())
    }
}
