package com.example.cleancity.notifications

import com.example.cleancity.shared.models.ComplaintStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationTextsTest {

    @Test
    fun `IN_PROGRESS uses INFO icon and 'принята в работу' wording`() {
        val t = NotificationTexts.statusChange(
            "Мусор · Транспортная",
            ComplaintStatus.IN_PROGRESS,
            "Бригада выехала"
        )
        assertEquals("Ваша жалоба принята в работу", t.title)
        assertEquals("«Мусор · Транспортная» — в работе. Бригада выехала", t.body)
        assertEquals("INFO", t.iconStyle)
    }

    @Test
    fun `RESOLVED uses SUCCESS icon and 'решена' wording`() {
        val t = NotificationTexts.statusChange(
            "Мусор · Транспортная",
            ComplaintStatus.RESOLVED,
            "Убрано 2026-05-12"
        )
        assertEquals("Ваша жалоба решена", t.title)
        assertTrue(t.body.startsWith("«Мусор · Транспортная» — решена."))
        assertTrue(t.body.contains("Убрано 2026-05-12"))
        assertEquals("SUCCESS", t.iconStyle)
    }

    @Test
    fun `REJECTED includes admin comment in 'Комментарий муниципальных служб' block`() {
        val t = NotificationTexts.statusChange(
            "Свалка",
            ComplaintStatus.REJECTED,
            "Не подтверждено инспектором"
        )
        assertEquals("Жалоба отклонена", t.title)
        assertTrue(t.body.contains("закрыта со статусом «Отклонена»"))
        assertTrue(t.body.contains("Комментарий муниципальных служб: Не подтверждено инспектором"))
        assertEquals("WARNING", t.iconStyle)
    }

    @Test
    fun `DUPLICATE wording mentions duplicate`() {
        val t = NotificationTexts.statusChange(
            "Свалка",
            ComplaintStatus.DUPLICATE,
            "Дублирует #42"
        )
        assertEquals("Жалоба отмечена как дубликат", t.title)
        assertTrue(t.body.contains("закрыта со статусом «Дубликат»"))
        assertTrue(t.body.contains("Комментарий муниципальных служб: Дублирует #42"))
        assertEquals("WARNING", t.iconStyle)
    }

    @Test
    fun `NEW throws because no notification should be sent for creation`() {
        assertFailsWith<IllegalStateException> {
            NotificationTexts.statusChange("X", ComplaintStatus.NEW, "irrelevant")
        }
    }
}
