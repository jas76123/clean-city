package com.example.cleancity.ui.feature.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Данные одного in-app баннера уведомления. */
data class BannerData(
    val title: String,
    val notificationId: Long?,
)

/**
 * Координатор глобального in-app баннера. Синглтон в Koin.
 *
 * Показывается по одному баннеру за раз — новый вытесняет предыдущий.
 * Также хранит высоту нижней панели вкладок ([bottomBarHeight]), которую
 * публикует MainShellScreen, чтобы оверлей в App.kt вставал НАД панелью,
 * а не перекрывал её кнопки.
 */
class BannerController {
    private val _current = MutableStateFlow<BannerData?>(null)
    val current: StateFlow<BannerData?> = _current.asStateFlow()

    private val _bottomBarHeight = MutableStateFlow(0.dp)
    val bottomBarHeight: StateFlow<Dp> = _bottomBarHeight.asStateFlow()

    fun show(data: BannerData) { _current.value = data }

    fun dismiss() { _current.value = null }

    fun setBottomBarHeight(height: Dp) { _bottomBarHeight.value = height }
}
