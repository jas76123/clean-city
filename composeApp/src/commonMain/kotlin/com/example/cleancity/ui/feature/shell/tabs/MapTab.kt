package com.example.cleancity.ui.feature.shell.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.example.cleancity.ui.feature.map.MapScreen

object MapTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Map)
            return remember { TabOptions(index = 1u, title = "Карта", icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(MapScreen())
    }
}
