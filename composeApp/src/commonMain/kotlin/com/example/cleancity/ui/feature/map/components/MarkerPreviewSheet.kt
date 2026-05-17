package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerPreviewSheet(
    marker: MapMarker,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "${marker.category.localizedLabel} · ${marker.status.localizedLabel()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Координаты: ${formatCoord(marker.latitude)}, ${formatCoord(marker.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Открыть детально (Day 10)") }
        }
    }
}

private fun formatCoord(value: Double): String {
    val rounded = (value * 10000).roundToLong()
    val whole = rounded / 10000
    val frac = (rounded % 10000).let { if (it < 0) -it else it }
    val fracStr = frac.toString().padStart(4, '0')
    return "$whole.$fracStr"
}

private fun ComplaintStatus.localizedLabel(): String = when (this) {
    ComplaintStatus.NEW -> "Новая"
    ComplaintStatus.IN_PROGRESS -> "В работе"
    ComplaintStatus.RESOLVED -> "Решено"
    ComplaintStatus.REJECTED -> "Отклонено"
    ComplaintStatus.DUPLICATE -> "Дубликат"
}
