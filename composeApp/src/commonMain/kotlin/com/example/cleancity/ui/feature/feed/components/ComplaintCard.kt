package com.example.cleancity.ui.feature.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.ui.components.CategoryIcon
import com.example.cleancity.ui.components.emoji
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green500
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green800
import com.example.cleancity.ui.theme.Green900
import com.example.cleancity.ui.theme.Red

@Composable
fun ComplaintCard(
    complaint: ComplaintResponse,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = Gray100,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoPlaceholder(complaint)
        TitleRow(complaint)
        AddressRow(address = complaint.address, district = complaint.district)
        MetaRow(complaint)
    }
}

@Composable
private fun PhotoPlaceholder(complaint: ComplaintResponse) {
    val firstThumb = complaint.photos.minByOrNull { it.sortOrder }?.thumbUrl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        if (firstThumb != null) {
            coil3.compose.AsyncImage(
                model = firstThumb,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            val gradient = when (complaint.status) {
                ComplaintStatus.RESOLVED -> Brush.linearGradient(listOf(Green500, Green800))
                ComplaintStatus.IN_PROGRESS -> Brush.linearGradient(listOf(Blue, Green700))
                else -> Brush.linearGradient(listOf(Green700, Green900))
            }
            Box(modifier = Modifier.fillMaxSize().background(gradient), contentAlignment = Alignment.Center) {
                CategoryIcon(category = complaint.category, size = 40.dp)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${complaint.category.emoji()} ${complaint.category.localizedLabel}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun TitleRow(complaint: ComplaintResponse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = complaint.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Spacer(Modifier.padding(start = 8.dp))
        StatusBadge(status = complaint.status)
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
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

@Composable
private fun AddressRow(address: String, district: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Gray500,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = if (district.isNullOrBlank()) address else "$address · $district",
            style = MaterialTheme.typography.bodySmall,
            color = Gray500,
            maxLines = 1,
        )
    }
}

@Composable
private fun MetaRow(complaint: ComplaintResponse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Дата + автор слева. Группа взвешена, имя автора ужимается в «…»,
        // чтобы длинное «Удалённый пользователь» не распирало карточку.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = relativeTime(complaint.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
                maxLines = 1,
            )
            complaint.authorName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = " · 👤 $name",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.padding(start = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = null,
                tint = Green500,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = complaint.votesCount.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Gray700,
            )
        }
    }
}

/**
 * Минимальный relative-time из ISO-8601 строки. Точность: «сегодня» / «вчера» / «N дн назад» / fallback на дату.
 * Для MVP достаточно — на Day 13 можно подменить полноценным парсером kotlinx-datetime.
 */
private fun relativeTime(iso: String): String {
    val datePart = iso.take(10).takeIf { it.length == 10 } ?: return iso
    val today = kotlinx.datetime.Clock.System.now()
        .toString().take(10)
    return when {
        datePart == today -> "Сегодня"
        else -> datePart
    }
}
