package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
            AddressSuggestionList(
                suggestions = suggestions,
                onSuggestionClick = onSuggestionClick,
            )
        }
    }
}
