package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.feature.map.MapSuggestion
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Green700

@Composable
fun MapSearchBar(
    query: String,
    suggestions: List<MapSuggestion>,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (MapSuggestion) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск адреса в Сочи", color = Gray500) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Gray700)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = Gray700)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Green700,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Gray700,
                unfocusedTextColor = Gray700,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(28.dp), clip = false),
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), clip = false),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(suggestions, key = { it.id }) { item ->
                        SuggestionRow(item = item, onClick = { onSuggestionClick(item) })
                        if (item != suggestions.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(item: MapSuggestion, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 28.dp)) {
            Text(
                text = item.title,
                color = Gray700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            item.subtitle?.let {
                Text(
                    text = it,
                    color = Gray500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Green700,
            modifier = Modifier.size(20.dp),
        )
    }
}
