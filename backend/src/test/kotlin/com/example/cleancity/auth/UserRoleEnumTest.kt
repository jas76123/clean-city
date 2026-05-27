package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class UserRoleEnumTest {

    @Test
    fun `INSPECTOR value no longer exists`() {
        assertFailsWith<IllegalArgumentException> {
            UserRole.valueOf("INSPECTOR")
        }
    }

    @Test
    fun `only three roles remain`() {
        val names = UserRole.values().map { it.name }.toSet()
        assertEquals(setOf("RESIDENT", "OPERATOR", "ADMIN"), names)
    }
}
