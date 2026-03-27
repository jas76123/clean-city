package com.example.cleancity.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.ui.map.MapScreen
import com.example.cleancity.ui.theme.*

class MainTabScreen : Screen {

    @Composable
    override fun Content() {
        var selectedTab by remember { mutableStateOf(1) }
        val unreadCount by InMemoryRepository.notifications.collectAsState()

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                ) {
                    val tabs = listOf(
                        TabItem("Лента", "🏠", 0),
                        TabItem("Карта", "🗺", 1),
                        TabItem("Уведомл.", "🔔", 2),
                        TabItem("Чаты", "💬", 3),
                        TabItem("Профиль", "👤", 4),
                    )
                    val unread = unreadCount.count { !it.isRead }

                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab.index,
                            onClick = { selectedTab = tab.index },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab.index == 2 && unread > 0) {
                                            Badge(containerColor = Red) {
                                                Text("$unread", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Text(tab.icon, fontSize = 20.sp)
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedTab == tab.index) Green600 else Gray400,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Green600,
                                indicatorColor = Green100,
                            ),
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    1 -> MapScreen().Content()
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Скоро будет доступно", color = Gray400)
                        }
                    }
                }
            }
        }
    }
}

private data class TabItem(val label: String, val icon: String, val index: Int)
