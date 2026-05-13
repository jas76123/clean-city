package com.example.cleancity.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {

    @Test fun `emailFormat accepts valid emails`() {
        assertTrue(Validation.emailFormat("user@example.com"))
        assertTrue(Validation.emailFormat("a.b+tag@sub.example.co"))
    }

    @Test fun `emailFormat rejects missing at`() {
        assertFalse(Validation.emailFormat("userexample.com"))
    }

    @Test fun `emailFormat rejects missing dot`() {
        assertFalse(Validation.emailFormat("user@example"))
    }

    @Test fun `emailFormat rejects too short`() {
        assertFalse(Validation.emailFormat("a@b"))
    }

    @Test fun `emailFormat rejects empty`() {
        assertFalse(Validation.emailFormat(""))
    }

    @Test fun `passwordStrength accepts 8 chars`() {
        assertTrue(Validation.passwordStrength("12345678"))
    }

    @Test fun `passwordStrength rejects 7 chars`() {
        assertFalse(Validation.passwordStrength("1234567"))
    }

    @Test fun `passwordStrength rejects over 100 chars`() {
        assertFalse(Validation.passwordStrength("a".repeat(101)))
    }

    @Test fun `fullNameNonBlank rejects whitespace only`() {
        assertFalse(Validation.fullNameNonBlank("   "))
    }

    @Test fun `fullNameNonBlank accepts trimmed name`() {
        assertTrue(Validation.fullNameNonBlank("Иван Иванов"))
    }
}
