package com.example.cleancity.ui.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.StatusChangeResponse
import com.example.cleancity.ui.components.SectionTopBar
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.map.components.emoji
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray200
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green500
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green800
import com.example.cleancity.ui.theme.Green900
import com.example.cleancity.ui.theme.Red
import org.koin.core.parameter.parametersOf

class ComplaintDetailScreen(private val complaintId: Long) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: ComplaintDetailScreenModel = koinScreenModel(parameters = { parametersOf(complaintId) })
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHost = remember { SnackbarHostState() }
        var showLoginPrompt by remember { mutableStateOf(false) }
        var showOwnComplaintDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { model.load() }
        LaunchedEffect(state) {
            (state as? DetailState.Loaded)?.transientError?.let {
                snackbarHost.showSnackbar(it)
                model.clearTransientError()
            }
        }

        Scaffold(
            topBar = {
                SectionTopBar(title = "Жалоба", onBack = { navigator.pop() })
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    DetailState.Initial, DetailState.Loading -> CenteredSpinner()
                    is DetailState.Error -> ErrorView(s.message) { model.load() }
                    is DetailState.Loaded -> LoadedContent(
                        loaded = s,
                        onVoteClick = {
                            when {
                                s.isGuest -> showLoginPrompt = true
                                s.isOwnComplaint -> showOwnComplaintDialog = true
                                else -> model.toggleVote()
                            }
                        },
                    )
                }
            }
        }

        if (showLoginPrompt) {
            AlertDialog(
                onDismissRequest = { showLoginPrompt = false },
                title = { Text("Войдите, чтобы поддержать") },
                text = { Text("Только зарегистрированные жители могут подтверждать проблемы. Это нужно, чтобы голоса были честными.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLoginPrompt = false
                        navigator.push(LoginScreen())
                    }) { Text("Войти") }
                },
                dismissButton = {
                    TextButton(onClick = { showLoginPrompt = false }) { Text("Отмена") }
                },
            )
        }

        if (showOwnComplaintDialog) {
            AlertDialog(
                onDismissRequest = { showOwnComplaintDialog = false },
                title = { Text("Это ваша жалоба") },
                text = { Text("Вы автор этой жалобы — отозвать своё подтверждение нельзя. Удалить жалобу можно в разделе «Мои жалобы» личного кабинета.") },
                confirmButton = {
                    TextButton(onClick = { showOwnComplaintDialog = false }) { Text("Понятно") }
                },
            )
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = Gray600, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedContent(
    loaded: DetailState.Loaded,
    onVoteClick: () -> Unit,
) {
    val c = loaded.complaint
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { PhotoPager(c) }
        item {
            MetaSection(c, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
        }
        item {
            VoteCard(
                c = c,
                isVoting = loaded.isVoting,
                isGuest = loaded.isGuest,
                isOwnComplaint = loaded.isOwnComplaint,
                onClick = onVoteClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            DescriptionSection(c.description, modifier = Modifier.padding(16.dp))
        }
        resolutionComment(c.status, c.statusHistory)?.let { comment ->
            item {
                ResolutionBlock(
                    comment = comment,
                    duplicateOfId = c.duplicateOfId.takeIf { c.status == ComplaintStatus.DUPLICATE },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (c.statusHistory.isNotEmpty()) {
            item {
                Text(
                    "История",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Gray700,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            items(c.statusHistory.size) { i ->
                StatusHistoryRow(c.statusHistory[i], modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoPager(complaint: ComplaintResponse) {
    val photos = complaint.photos.sortedBy { it.sortOrder }
    val photoCount = photos.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { photoCount })

    Box(Modifier.fillMaxWidth().height(260.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val photo = photos.getOrNull(page)
            if (photo != null) {
                coil3.compose.AsyncImage(
                    model = photo.photoUrl,
                    contentDescription = "Фото ${page + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                // Жалоба без фото — fallback на градиент с эмодзи категории.
                val gradient = when (complaint.status) {
                    ComplaintStatus.RESOLVED -> Brush.linearGradient(listOf(Green500, Green800))
                    ComplaintStatus.IN_PROGRESS -> Brush.linearGradient(listOf(Blue, Green700))
                    else -> Brush.linearGradient(listOf(Green700, Green900))
                }
                Box(
                    Modifier.fillMaxSize().background(gradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = complaint.category.emoji(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
        if (photoCount > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(photoCount) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaSection(c: ComplaintResponse, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(c.status)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "${c.category.emoji()} ${c.category.localizedLabel}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Gray700,
            )
        }
        Text(
            text = c.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = if (c.district.isNullOrBlank()) c.address else "${c.address} · ${c.district}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
        }
        val author = c.authorName?.takeIf { it.isNotBlank() } ?: "Аноним"
        Text(
            text = "$author · ${c.createdAt.take(10)}",
            style = MaterialTheme.typography.bodySmall,
            color = Gray500,
        )
    }
}

@Composable
private fun StatusBadge(status: ComplaintStatus) {
    val (label, bg, fg) = when (status) {
        ComplaintStatus.NEW -> Triple("В обработке", Amber.copy(alpha = 0.18f), Amber)
        ComplaintStatus.IN_PROGRESS -> Triple("В работе", Blue.copy(alpha = 0.16f), Blue)
        ComplaintStatus.RESOLVED -> Triple("Решено", Accent.copy(alpha = 0.22f), Green700)
        ComplaintStatus.REJECTED -> Triple("Отклонено", Red.copy(alpha = 0.16f), Red)
        ComplaintStatus.DUPLICATE -> Triple("Дубликат", Gray500.copy(alpha = 0.16f), Gray700)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = fg)
    }
}

@Composable
private fun VoteCard(
    c: ComplaintResponse,
    isVoting: Boolean,
    isGuest: Boolean,
    isOwnComplaint: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canVote = c.status == ComplaintStatus.NEW ||
        c.status == ComplaintStatus.IN_PROGRESS ||
        c.status == ComplaintStatus.RESOLVED
    val buttonLabel = when {
        isGuest -> "Войти, чтобы поддержать"
        isOwnComplaint -> "Ваша жалоба"
        !canVote -> "Голосование закрыто"
        c.userVoted -> "Отозвать подтверждение"
        else -> "Подтверждаю"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Green600)
            Spacer(Modifier.size(6.dp))
            Text(
                "${c.votesCount} ${pluralVotes(c.votesCount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Gray900,
            )
            Spacer(Modifier.weight(1f))
            if (c.votesCount >= 50) {
                Text(
                    "ПРИОРИТЕТ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Green700,
                )
            }
        }
        Button(
            onClick = onClick,
            enabled = canVote && !isVoting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if ((c.userVoted || isOwnComplaint) && !isGuest) Gray200 else Accent,
                contentColor = if ((c.userVoted || isOwnComplaint) && !isGuest) Gray700 else Green900,
            ),
        ) {
            if (isVoting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = Green900,
                )
            } else {
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun pluralVotes(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "подтверждение"
        mod10 in 2..4 && mod100 !in 12..14 -> "подтверждения"
        else -> "подтверждений"
    }
}

@Composable
private fun DescriptionSection(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Column(modifier = modifier) {
        Text(
            "Описание",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Gray700,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray700,
        )
    }
}

@Composable
private fun ResolutionBlock(
    comment: String,
    duplicateOfId: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Red.copy(alpha = 0.08f))
            .border(1.dp, Red.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Решение администрации",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Red,
        )
        Text(
            comment,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray700,
        )
        if (duplicateOfId != null) {
            Text(
                "Дубликат жалобы #$duplicateOfId",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
        }
    }
}

@Composable
private fun StatusHistoryRow(change: StatusChangeResponse, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(change.toStatus)
            Spacer(Modifier.weight(1f))
            Text(change.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = Gray500)
        }
        if (change.comment.isNotBlank()) {
            Text(change.comment, style = MaterialTheme.typography.bodySmall, color = Gray700)
        }
        change.changedByName?.takeIf { it.isNotBlank() }?.let { name ->
            Text("— $name", style = MaterialTheme.typography.labelSmall, color = Gray500)
        }
    }
}
