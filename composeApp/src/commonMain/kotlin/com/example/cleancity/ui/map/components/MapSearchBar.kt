package com.example.cleancity.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.map.SearchSuggestion
import com.example.cleancity.ui.theme.*

@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<SearchSuggestion>,
    onSuggestionClick: (SearchSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск адреса...", color = Gray400) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp)),
        )

        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    suggestions.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(item) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Gray800)
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Gray400)
                            }
                        }
                    }
                }
            }
        }
    }
}
