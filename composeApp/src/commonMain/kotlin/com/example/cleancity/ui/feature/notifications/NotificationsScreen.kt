package com.example.cleancity.ui.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.notifications.components.NotificationCard
import com.example.cleancity.ui.feature.shell.tabs.FeedTab
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green700

class NotificationsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: NotificationsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(Unit) { model.load() }
        LaunchedEffect(state) {
            (state as? NotificationsState.Loaded)?.transientError?.let {
                snackbarHost.showSnackbar(it)
                model.clearTransientError()
            }
        }

        fun onClick(n: NotificationResponse) {
            model.markRead(n.id)
            when {
                n.complaintId != null -> navigator.push(ComplaintDetailScreen(n.complaintId!!))
                n.announcementId != null -> tabNavigator.current = FeedTab
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NotificationsTopBar(
                    showMarkAll = (state as? NotificationsState.Loaded)?.let { it.unreadCount > 0 } == true,
                    onMarkAll = model::markAllRead,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
                when (val s = state) {
                    NotificationsState.Initial, NotificationsState.Loading ->
                        LoadingState(Modifier.fillMaxSize())
                    NotificationsState.GuestPrompt ->
                        EmptyState(
                            emoji = "🔔",
                            title = "Войдите, чтобы видеть уведомления",
                            subtitle = "Уведомления о статусе ваших жалоб и объявления " +
                                "доступны после входа в аккаунт.",
                            action = {
                                Button(onClick = { navigator.push(LoginScreen()) }) { Text("Войти") }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    NotificationsState.Empty ->
                        EmptyState(
                            emoji = "🔔",
                            title = "Пока нет уведомлений",
                            subtitle = "Здесь появятся ответы администрации и объявления.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    is NotificationsState.Error ->
                        ErrorState(s.message, onRetry = { model.load() }, modifier = Modifier.fillMaxSize())
                    is NotificationsState.Loaded ->
                        NotificationsList(
                            loaded = s,
                            onRefresh = model::refresh,
                            onItemClick = ::onClick,
                        )
                }
            }
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun NotificationsTopBar(
    showMarkAll: Boolean,
    onMarkAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Уведомления",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
        if (showMarkAll) {
            TextButton(onClick = onMarkAll) { Text("Прочитать все", color = Green700) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsList(
    loaded: NotificationsState.Loaded,
    onRefresh: () -> Unit,
    onItemClick: (NotificationResponse) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loaded.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = loaded.items, key = { it.id }) { n ->
                NotificationCard(
                    notification = n,
                    modifier = Modifier.clickable { onItemClick(n) },
                )
            }
        }
    }
}

