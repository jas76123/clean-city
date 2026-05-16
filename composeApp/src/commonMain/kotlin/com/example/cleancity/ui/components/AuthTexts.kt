package com.example.cleancity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Green200
import com.example.cleancity.ui.theme.Green50
import com.example.cleancity.ui.theme.Green500
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900

@Composable
fun AuthTag(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Green50)
            .border(1.dp, Green200, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Green500))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Green700,
        )
    }
}

@Composable
fun AuthTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Gray900,
        modifier = modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
fun AuthSub(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Gray500,
        modifier = modifier.padding(bottom = 32.dp),
    )
}
