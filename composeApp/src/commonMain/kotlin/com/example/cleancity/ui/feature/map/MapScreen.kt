package com.example.cleancity.ui.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.domain.location.rememberLocationPermission
import com.example.cleancity.ui.feature.create.CreateComplaintPlaceholderScreen
import com.example.cleancity.ui.feature.map.components.CategoryFilterChips
import com.example.cleancity.ui.feature.map.components.CategorySheet
import com.example.cleancity.ui.feature.map.components.MapFabGroup
import com.example.cleancity.ui.feature.map.components.MapLegend
import com.example.cleancity.ui.feature.map.components.MarkerPreviewSheet

class MapScreen(private val onLogout: () -> Unit) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: MapScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val permission = rememberLocationPermission()
        val snackbarHost = remember { SnackbarHostState() }
        var menuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(state.error) {
            state.error?.let {
                snackbarHost.showSnackbar(it)
                model.clearError()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Чистый Город") },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Выйти") },
                                onClick = { menuOpen = false; onLogout() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    CategoryFilterChips(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { model.selectCategory(it) },
                        onMoreClicked = { model.openCategorySheet() },
                    )
                    Box(Modifier.fillMaxSize()) {
                        YandexMapHost(
                            cameraPosition = state.cameraPosition,
                            markers = state.markers,
                            onCameraMoved = model::onCameraMoved,
                            onMarkerClick = model::onMarkerClick,
                            onClusterTap = { bbox ->
                                val midLat = (bbox.swLat + bbox.neLat) / 2.0
                                val midLon = (bbox.swLon + bbox.neLon) / 2.0
                                val newZoom = (state.cameraPosition.zoom + 1.5f).coerceAtMost(20f)
                                model.onCameraMoved(bbox)
                                model.zoomTo(midLat, midLon, newZoom)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (state.isLoading && state.markers.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (state.isLoading && state.markers.isNotEmpty()) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopStart),
                            )
                        }
                        MapLegend(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 16.dp),
                        )
                        MapFabGroup(
                            onLocationClick = {
                                model.onLocationFabClicked(permission.status, permission.launchRequest)
                            },
                            onCreateClick = { navigator.push(CreateComplaintPlaceholderScreen()) },
                            isLocating = state.isLocating,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }

                state.selectedMarkerId?.let { id ->
                    val marker = state.markers.firstOrNull { it.id == id }
                    if (marker != null) {
                        MarkerPreviewSheet(marker = marker, onDismiss = { model.closeMarkerSheet() })
                    }
                }

                if (state.isCategorySheetOpen) {
                    CategorySheet(
                        initialSelection = state.selectedCategory,
                        onApply = { model.selectCategory(it) },
                        onDismiss = { model.closeCategorySheet() },
                    )
                }
            }
        }
    }
}
