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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.feed.components.AnnouncementCard
import com.example.cleancity.ui.feature.feed.components.ComplaintCard
import com.example.cleancity.ui.feature.feed.components.FeedToggle
import com.example.cleancity.ui.feature.feed.components.FeedTopBar
import com.example.cleancity.ui.theme.Gray500

class FeedScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: FeedScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.loadInitial() }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FeedTopBar(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))

            when (val s = state) {
                FeedState.Initial, FeedState.Loading -> LoadingState(Modifier.fillMaxSize())
                is FeedState.Error -> ErrorState(
                    message = s.message,
                    onRetry = { model.loadInitial() },
                    modifier = Modifier.fillMaxSize(),
                )
                is FeedState.Loaded -> FeedLoadedContent(
                    loaded = s,
                    onRefresh = model::refresh,
                    onModeChange = model::setMode,
                    onComplaintClick = { id -> navigator.push(ComplaintDetailScreen(id)) },
                    onAnnouncementClick = { ann -> navigator.push(AnnouncementDetailScreen(ann)) },
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
    onAnnouncementClick: (AnnouncementResponse) -> Unit,
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
                item {
                    AnnouncementsSection(
                        announcements = loaded.announcements,
                        onAnnouncementClick = onAnnouncementClick,
                    )
                }
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
                item {
                    val title = when {
                        loaded.mode == FeedMode.MINE && loaded.isGuest -> "Войдите в аккаунт"
                        loaded.mode == FeedMode.MINE -> "У вас пока нет жалоб"
                        else -> "Пока нет жалоб поблизости"
                    }
                    val subtitle = when {
                        loaded.mode == FeedMode.MINE && loaded.isGuest ->
                            "Чтобы видеть свои жалобы, войдите в аккаунт."
                        loaded.mode == FeedMode.MINE ->
                            "Создайте первую — нажмите ➕ на карте."
                        else -> "Станьте первым — нажмите ➕ на карте."
                    }
                    EmptyState(
                        emoji = "📋",
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
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
private fun AnnouncementsSection(
    announcements: List<AnnouncementResponse>,
    onAnnouncementClick: (AnnouncementResponse) -> Unit,
) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📣 ОБЪЯВЛЕНИЯ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = Gray500,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = announcements, key = { it.id }) { ann ->
                AnnouncementCard(
                    announcement = ann,
                    modifier = Modifier
                        .width(280.dp)
                        .clickable { onAnnouncementClick(ann) },
                )
            }
        }
    }
}

