package com.example.cleancity.ui.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.app_logo
import com.example.cleancity.domain.location.rememberLocationPermission
import com.example.cleancity.ui.feature.create.CreateComplaintScreen
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.map.components.CategoryFilterChips
import com.example.cleancity.ui.feature.map.components.CategorySheet
import com.example.cleancity.ui.feature.map.components.MapFabGroup
import com.example.cleancity.ui.feature.map.components.MapLegend
import com.example.cleancity.ui.feature.map.components.MapSearchBar
import com.example.cleancity.ui.feature.map.components.MarkerPreviewSheet
import org.jetbrains.compose.resources.painterResource

class MapScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: MapScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val permission = rememberLocationPermission()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(state.error) {
            state.error?.let {
                snackbarHost.showSnackbar(it)
                model.clearError()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                            Text("Чистый Город")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                YandexMapHost(
                    cameraPosition = state.cameraPosition,
                    markers = state.markers,
                    onCameraMoved = model::onCameraMoved,
                    onMarkerClick = model::onMarkerClick,
                    onClusterTap = { bbox ->
                        val midLat = (bbox.swLat + bbox.neLat) / 2.0
                        val midLon = (bbox.swLon + bbox.neLon) / 2.0
                        model.zoomTo(midLat, midLon, bbox.suggestedZoom())
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
                ) {
                    MapSearchBar(
                        query = state.searchQuery,
                        suggestions = state.searchSuggestions,
                        onQueryChange = model::onSearchQueryChange,
                        onSuggestionClick = model::onSuggestionSelected,
                        onClear = model::clearSearch,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                    CategoryFilterChips(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { model.selectCategory(it) },
                        onMoreClicked = { model.openCategorySheet() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.isLoading && !state.hasInitialDataLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
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
                    onCreateClick = { navigator.push(CreateComplaintScreen()) },
                    isLocating = state.isLocating,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                state.selectedMarkerId?.let { id ->
                    val marker = state.markers.firstOrNull { it.id == id }
                    if (marker != null) {
                        MarkerPreviewSheet(
                            marker = marker,
                            onDismiss = { model.closeMarkerSheet() },
                            onOpenDetail = {
                                model.closeMarkerSheet()
                                navigator.push(ComplaintDetailScreen(marker.id))
                            },
                        )
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
