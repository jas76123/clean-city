package com.example.cleancity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.ui.theme.Green50

/** Эмодзи категории в едином круглом контейнере. Размер эмодзи = половина диаметра. */
@Composable
fun CategoryIcon(
    category: ProblemCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Green50),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.emoji(),
            style = TextStyle(fontSize = (size.value * 0.5f).sp),
        )
    }
}
