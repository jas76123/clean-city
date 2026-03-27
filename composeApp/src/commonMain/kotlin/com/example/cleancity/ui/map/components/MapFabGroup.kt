package com.example.cleancity.ui.map.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.*

@Composable
fun MapFabGroup(
    onCreateClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FloatingActionButton(
            onClick = onCreateClick,
            containerColor = Accent,
            contentColor = Green900,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Text("+", fontSize = 22.sp)
        }
        FloatingActionButton(
            onClick = onLocationClick,
            containerColor = Color.White,
            contentColor = Gray700,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Text("◎", fontSize = 18.sp)
        }
    }
}
