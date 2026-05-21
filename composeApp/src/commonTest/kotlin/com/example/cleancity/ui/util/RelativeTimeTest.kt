package com.example.cleancity.ui.util

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = Instant.parse("2026-05-21T12:00:00Z")

    @Test fun `less than a minute is just now`() {
        assertEquals("только что", relativeTime("2026-05-21T11:59:30Z", now))
    }

    @Test fun `minutes ago`() {
        assertEquals("5 мин назад", relativeTime("2026-05-21T11:55:00Z", now))
    }

    @Test fun `hours ago`() {
        assertEquals("3 ч назад", relativeTime("2026-05-21T09:00:00Z", now))
    }

    @Test fun `yesterday`() {
        assertEquals("вчера", relativeTime("2026-05-20T10:00:00Z", now))
    }

    @Test fun `several days ago`() {
        assertEquals("4 дн назад", relativeTime("2026-05-17T12:00:00Z", now))
    }

    @Test fun `older than a week falls back to date`() {
        assertEquals("2026-05-01", relativeTime("2026-05-01T12:00:00Z", now))
    }

    @Test fun `unparseable string falls back to first ten chars`() {
        assertEquals("garbage-st", relativeTime("garbage-string", now))
    }
}
