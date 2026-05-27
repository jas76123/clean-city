package com.example.cleancity.ui.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
import com.example.cleancity.ui.components.SectionTopBar
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.util.relativeTime

class AnnouncementDetailScreen private constructor(
    private val initial: AnnouncementResponse?,
    private val announcementId: Long,
) : Screen {

    constructor(announcement: AnnouncementResponse) : this(announcement, announcement.id)
    constructor(id: Long) : this(null, id)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AnnouncementDetailScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        LaunchedEffect(announcementId) { model.start(initial, announcementId) }

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            SectionTopBar(title = "Объявление", onBack = { navigator.pop() })

            when (val s = state) {
                AnnouncementDetailState.Loading -> LoadingState(Modifier.fillMaxSize())
                is AnnouncementDetailState.Error -> ErrorState(
                    message = s.message,
                    onRetry = { model.load(announcementId) },
                    modifier = Modifier.fillMaxSize(),
                )
                is AnnouncementDetailState.Loaded -> Body(s.announcement)
            }
        }
    }
}

@Composable
private fun Body(announcement: AnnouncementResponse) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (labelText, labelColor) = when (announcement.iconStyle) {
            IconStyle.SUCCESS -> "✓ ХОРОШИЕ НОВОСТИ" to Accent
            IconStyle.WARNING -> "⚠ ВНИМАНИЕ" to Amber
            IconStyle.INFO -> "ⓘ ИНФО" to Blue
        }
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = labelColor,
        )
        Text(
            text = announcement.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
        Text(
            text = announcement.body,
            style = MaterialTheme.typography.bodyLarge,
            color = Gray700,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(4.dp))
        MetaRow(label = "Опубликовано", value = relativeTime(announcement.publishedAt))
        announcement.expiresAt?.let {
            MetaRow(label = "Срок действия", value = it.take(10))
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Gray500,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray700,
        )
    }
}
