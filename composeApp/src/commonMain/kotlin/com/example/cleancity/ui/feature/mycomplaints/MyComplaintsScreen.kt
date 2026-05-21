package com.example.cleancity.ui.feature.mycomplaints

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
import com.example.cleancity.ui.feature.feed.components.ComplaintCard
import com.example.cleancity.ui.theme.Gray900

class MyComplaintsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: MyComplaintsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.load() }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(
                onBack = { navigator.pop() },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            when (val s = state) {
                MyComplaintsState.Initial, MyComplaintsState.Loading ->
                    LoadingState(Modifier.fillMaxSize())
                MyComplaintsState.Empty ->
                    EmptyState(
                        emoji = "📋",
                        title = "Вы пока не создавали жалоб",
                        subtitle = "Заметили проблему — нажмите ➕ на карте.",
                        modifier = Modifier.fillMaxSize(),
                    )
                is MyComplaintsState.Error ->
                    ErrorState(s.message, onRetry = { model.load() }, modifier = Modifier.fillMaxSize())
                is MyComplaintsState.Loaded -> LoadedList(
                    loaded = s,
                    onRefresh = model::refresh,
                    onLoadMore = model::loadNextPage,
                    onComplaintClick = { id -> navigator.push(ComplaintDetailScreen(id)) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Text(
            "Мои жалобы",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedList(
    loaded: MyComplaintsState.Loaded,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComplaintClick: (Long) -> Unit,
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
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    PullToRefreshBox(
        isRefreshing = loaded.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
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

