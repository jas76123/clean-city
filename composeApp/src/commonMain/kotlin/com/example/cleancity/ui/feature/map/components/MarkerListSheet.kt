package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.MapMarker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerListSheet(
    markers: List<MapMarker>,
    onDismiss: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text(
                text = "${markers.size} жалоб в этой точке",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(markers, key = { it.id }) { marker ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(marker.id) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = "${marker.category.localizedLabel} · ${marker.status.localizedLabel()}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Координаты: ${formatCoord(marker.latitude)}, ${formatCoord(marker.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
