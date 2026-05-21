package com.example.cleancity.ui.util

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListErrorMessageTest {

    @Test fun `network error gives connection message`() {
        val msg = listErrorMessage(IOException("no route to host"))
        assertEquals("Нет соединения. Проверьте интернет и попробуйте позже.", msg)
    }

    @Test fun `server 5xx gives server-unavailable message`() {
        val err = ApiException(ApiError("INTERNAL", "boom"), httpStatus = 503)
        assertEquals("Сервер временно недоступен. Попробуйте позже.", listErrorMessage(err))
    }

    @Test fun `client 4xx falls back to generic message`() {
        val err = ApiException(ApiError("BAD_REQUEST", "bad"), httpStatus = 400)
        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", listErrorMessage(err))
    }

    @Test fun `unknown throwable falls back to generic message`() {
        assertEquals(
            "Что-то пошло не так. Попробуйте ещё раз.",
            listErrorMessage(RuntimeException("???")),
        )
    }

    @Test fun `message never leaks raw exception text`() {
        val msg = listErrorMessage(RuntimeException("kotlin.NullPointerException at line 42"))
        assertTrue("line 42" !in msg)
    }
}
