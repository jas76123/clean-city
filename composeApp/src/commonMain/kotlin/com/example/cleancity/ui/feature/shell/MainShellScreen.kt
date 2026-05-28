package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.NotificationTapBus
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.ui.feature.shell.tabs.FeedTab
import com.example.cleancity.ui.feature.shell.tabs.MapTab
import com.example.cleancity.ui.feature.shell.tabs.NotificationsTab
import com.example.cleancity.ui.feature.shell.tabs.ProfileTab
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Green900
import org.koin.compose.koinInject

class MainShellScreen : Screen {
    @Composable
    override fun Content() {
        val store: UnreadCountStore = koinInject()
        val authRepo: AuthRepository = koinInject()
        val bus: NotificationEventBus = koinInject()
        val authState by authRepo.state.collectAsState()
        val unreadCount by store.state.collectAsState()
        val pendingTap by NotificationTapBus.pending.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated) store.start() else store.stop()
        }
        DisposableEffect(Unit) {
            onDispose { store.stop() }
        }

        TabNavigator(FeedTab) {
            val tabNavigator = LocalTabNavigator.current

            // 1. In-app banner подписка
            LaunchedEffect(Unit) {
                bus.newAnnouncements.collect { n ->
                    val result = snackbarHost.showSnackbar(
                        message = n.title,
                        actionLabel = "Посмотреть",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        tabNavigator.current = NotificationsTab
                    }
                }
            }

            // 2. Тап по системному push — переключить на NotificationsTab.
            LaunchedEffect(pendingTap) {
                val id = pendingTap ?: return@LaunchedEffect
                tabNavigator.current = NotificationsTab
                NotificationTapBus.consume(id)
            }

            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = {
                    SnackbarHost(snackbarHost) { data -> AnnouncementInAppBanner(data) }
                },
                content = { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        TabNavigationItem(FeedTab)
                        TabNavigationItem(MapTab)
                        TabNavigationItem(NotificationsTab, badgeCount = unreadCount)
                        TabNavigationItem(ProfileTab)
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab, badgeCount: Int = 0) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current.key == tab.key
    NavigationBarItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        icon = {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                    },
                ) {
                    Icon(
                        painter = tab.options.icon!!,
                        contentDescription = tab.options.title,
                    )
                }
            } else {
                Icon(
                    painter = tab.options.icon!!,
                    contentDescription = tab.options.title,
                )
            }
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Green900,
            indicatorColor = Accent,
            unselectedIconColor = Gray500,
        ),
    )
}
