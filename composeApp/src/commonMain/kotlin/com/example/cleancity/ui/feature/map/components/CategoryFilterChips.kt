package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ProblemCategory

private val TOP_6 = listOf(
    ProblemCategory.GARBAGE,
    ProblemCategory.ROADS,
    ProblemCategory.LIGHTING,
    ProblemCategory.GREENERY,
    ProblemCategory.SIDEWALKS,
    ProblemCategory.LANDSCAPING,
)

@Composable
fun CategoryFilterChips(
    selectedCategory: ProblemCategory?,
    onCategorySelected: (ProblemCategory?) -> Unit,
    onMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items: List<Any> = buildList {
        add(AllChip)
        addAll(TOP_6)
        add(MoreChip)
    }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(items) { item ->
            when (item) {
                AllChip -> FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("Все") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                MoreChip -> {
                    val moreLabel = selectedCategory
                        ?.takeIf { it !in TOP_6 }
                        ?.localizedLabel
                        ?: "Ещё"
                    FilterChip(
                        selected = selectedCategory != null && selectedCategory !in TOP_6,
                        onClick = onMoreClicked,
                        label = { Text(moreLabel) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                is ProblemCategory -> FilterChip(
                    selected = selectedCategory == item,
                    onClick = {
                        onCategorySelected(if (selectedCategory == item) null else item)
                    },
                    label = { Text(item.localizedLabel) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

private object AllChip
private object MoreChip
