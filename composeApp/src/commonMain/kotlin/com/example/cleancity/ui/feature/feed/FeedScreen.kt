package com.example.cleancity.ui.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.feed.components.AnnouncementCard
import com.example.cleancity.ui.feature.feed.components.ComplaintCard
import com.example.cleancity.ui.feature.feed.components.FeedToggle
import com.example.cleancity.ui.feature.feed.components.FeedTopBar
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import org.koin.compose.koinInject

class FeedScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: FeedScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val unreadStore: UnreadCountStore = koinInject()
        val unreadCount by unreadStore.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.loadInitial() }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FeedTopBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                unreadCount = unreadCount,
                onBellClick = { /* no-op; уведомления — отдельный таб */ },
            )

            when (val s = state) {
                FeedState.Initial, FeedState.Loading -> LoadingState()
                is FeedState.Error -> ErrorState(message = s.message, onRetry = { model.loadInitial() })
                is FeedState.Loaded -> FeedLoadedContent(
                    loaded = s,
                    onRefresh = model::refresh,
                    onModeChange = model::setMode,
                    onComplaintClick = { id -> navigator.push(ComplaintDetailScreen(id)) },
                    onLoadMore = model::loadNextPage,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedLoadedContent(
    loaded: FeedState.Loaded,
    onRefresh: () -> Unit,
    onModeChange: (FeedMode) -> Unit,
    onComplaintClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    val shouldLoadMore by remember(loaded) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5 && !loaded.endReached && !loaded.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = loaded.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (loaded.announcements.isNotEmpty()) {
                item { AnnouncementsSection(announcements = loaded.announcements) }
            }
            item {
                FeedToggle(
                    mode = loaded.mode,
                    mineCount = loaded.complaints.takeIf { loaded.mode == FeedMode.MINE }?.size ?: 0,
                    showMineCount = loaded.mode == FeedMode.MINE,
                    isGuest = loaded.isGuest,
                    onModeChange = onModeChange,
                )
            }
            if (loaded.complaints.isEmpty()) {
                item { EmptyFeedRow(mode = loaded.mode, isGuest = loaded.isGuest) }
            } else {
                items(items = loaded.complaints, key = { it.id }) { complaint ->
                    ComplaintCard(
                        complaint = complaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onComplaintClick(complaint.id) },
                    )
                }
                if (loaded.isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(strokeWidth = 2.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementsSection(announcements: List<AnnouncementResponse>) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📣 АДМИНИСТРАЦИЯ СОЧИ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = Gray500,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Все →",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = announcements, key = { it.id }) { ann ->
                AnnouncementCard(announcement = ann, modifier = Modifier.width(280.dp))
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Gray600,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
private fun EmptyFeedRow(mode: FeedMode, isGuest: Boolean) {
    val text = when {
        mode == FeedMode.MINE && isGuest -> "Войдите, чтобы видеть свои жалобы"
        mode == FeedMode.MINE -> "У вас пока нет жалоб. Создайте первую через ➕ на карте."
        else -> "Пока нет жалоб в Сочи. Стать первым — нажмите ➕ на карте."
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center,
        )
    }
}
