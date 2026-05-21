package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordPolicyTest {

    @Test
    fun `valid admin password passes`() {
        PasswordPolicy.validate("Secret123!xyz", UserRole.ADMIN)
    }

    @Test
    fun `admin password shorter than 12 is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Ab1!", UserRole.ADMIN)
        }
        assertTrue(ex.message!!.contains("12"))
    }

    @Test
    fun `admin password without digit is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Abcdefgh!xyz", UserRole.ADMIN)
        }
        assertEquals("Password must contain a digit", ex.message)
    }

    @Test
    fun `admin password without uppercase is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("secret123!xyz", UserRole.ADMIN)
        }
        assertEquals("Password must contain an uppercase letter", ex.message)
    }

    @Test
    fun `admin password without special char is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Secret123xyzAB", UserRole.ADMIN)
        }
        assertEquals("Password must contain a special character", ex.message)
    }

    @Test
    fun `resident password of 8 chars passes without char-class rules`() {
        PasswordPolicy.validate("simple12", UserRole.RESIDENT)
    }

    @Test
    fun `resident password shorter than 8 is rejected`() {
        assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("short", UserRole.RESIDENT)
        }
    }
}
