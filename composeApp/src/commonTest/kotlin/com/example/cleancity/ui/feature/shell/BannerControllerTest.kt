package com.example.cleancity.ui.feature.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BannerControllerTest {

    @Test
    fun show_setsCurrentBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Привет", notificationId = 42L))
        assertEquals("Привет", controller.current.value?.title)
        assertEquals(42L, controller.current.value?.notificationId)
    }

    @Test
    fun dismiss_clearsCurrentBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Привет", notificationId = 42L))
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun show_replacesPreviousBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Первый", notificationId = 1L))
        controller.show(BannerData(title = "Второй", notificationId = 2L))
        assertEquals("Второй", controller.current.value?.title)
        assertEquals(2L, controller.current.value?.notificationId)
    }

    @Test
    fun bottomBarHeight_defaultsToZero_andUpdates() {
        val controller = BannerController()
        assertEquals(0.dp, controller.bottomBarHeight.value)
        controller.setBottomBarHeight(80.dp)
        assertEquals(80.dp, controller.bottomBarHeight.value)
    }
}
