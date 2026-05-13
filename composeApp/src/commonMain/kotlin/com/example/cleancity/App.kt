package com.example.cleancity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cleancity.ui.theme.CleanCityTheme

@Composable
fun App() {
    CleanCityTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("CleanCity — Day 8 setup in progress")
        }
    }
}
