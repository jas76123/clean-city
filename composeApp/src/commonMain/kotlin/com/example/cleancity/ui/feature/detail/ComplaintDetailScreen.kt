package com.example.cleancity.ui.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen

class ComplaintDetailScreen(private val complaintId: Long) : Screen {
    @Composable
    override fun Content() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Деталь жалобы #$complaintId (в разработке)",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
