package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.example.cleancity.ui.feature.shell.tabs.FeedTab
import com.example.cleancity.ui.feature.shell.tabs.MapTab
import com.example.cleancity.ui.feature.shell.tabs.NotificationsTab
import com.example.cleancity.ui.feature.shell.tabs.ProfileTab
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900

class MainShellScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(FeedTab) {
            Scaffold(
                content = { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    NavigationBar {
                        TabNavigationItem(FeedTab)
                        TabNavigationItem(MapTab)
                        TabNavigationItem(NotificationsTab)
                        TabNavigationItem(ProfileTab)
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current.key == tab.key
    NavigationBarItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        icon = {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
            )
        },
        label = { Text(tab.options.title) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Green900,
            selectedTextColor = Green700,
            indicatorColor = Accent,
            unselectedIconColor = Gray500,
            unselectedTextColor = Gray500,
        ),
    )
}
