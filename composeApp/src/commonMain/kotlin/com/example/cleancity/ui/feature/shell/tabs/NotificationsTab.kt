package com.example.cleancity.ui.feature.shell.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.example.cleancity.ui.feature.notifications.NotificationsScreen

object NotificationsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Notifications)
            return remember { TabOptions(index = 2u, title = "Уведомл.", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(NotificationsScreen())
    }
}
