package com.example.cleancity.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun `tryAcquire blocks once limit is exceeded within window`() {
        var clock = 0L
        val limiter = RateLimiter { clock }

        // limit=3, window=10s. 4-я попытка должна быть отклонена.
        repeat(3) {
            assertTrue(limiter.tryAcquire("login", "1.1.1.1", limit = 3, windowSeconds = 10))
        }
        assertFalse(limiter.tryAcquire("login", "1.1.1.1", 3, 10))

        // А с другого IP — отдельный бакет, всё ещё разрешено.
        assertTrue(limiter.tryAcquire("login", "2.2.2.2", 3, 10))
    }

    @Test
    fun `bucket resets after window expires`() {
        var clock = 0L
        val limiter = RateLimiter { clock }

        repeat(3) { limiter.tryAcquire("login", "1.1.1.1", 3, windowSeconds = 10) }
        assertFalse(limiter.tryAcquire("login", "1.1.1.1", 3, 10))

        clock += 11_000  // окно прошло
        assertTrue(limiter.tryAcquire("login", "1.1.1.1", 3, 10))
    }

    @Test
    fun `different buckets are independent`() {
        val limiter = RateLimiter { 0L }
        repeat(3) { limiter.tryAcquire("login", "1.1.1.1", 3, 60) }
        // login исчерпан, forgot — нет.
        assertFalse(limiter.tryAcquire("login", "1.1.1.1", 3, 60))
        assertTrue(limiter.tryAcquire("forgot", "1.1.1.1", 3, 60))
    }
}
