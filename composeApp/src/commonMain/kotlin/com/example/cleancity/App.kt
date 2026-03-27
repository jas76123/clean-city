package com.example.cleancity

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.ui.navigation.MainTabScreen
import com.example.cleancity.ui.theme.CleanCityTheme

@Composable
fun App() {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        InMemoryRepository.loadSampleData()
    }

    CleanCityTheme {
        Navigator(MainTabScreen())
    }
}
