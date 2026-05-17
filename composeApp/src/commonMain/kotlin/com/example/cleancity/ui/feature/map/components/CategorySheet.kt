package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ProblemCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    initialSelection: ProblemCategory?,
    onApply: (ProblemCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf(initialSelection) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
            Text("Выберите категорию", modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(ProblemCategory.entries) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = pending == category,
                                onClick = { pending = category },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = pending == category,
                            onClick = { pending = category },
                        )
                        Text(
                            category.localizedLabel,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Button(
                onClick = { onApply(pending) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Применить") }
        }
    }
}
