package com.example.cleancity.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.model.Problem
import com.example.cleancity.model.CleanupEvent
import com.example.cleancity.ui.map.components.*

class MapScreen : Screen {

    @Composable
    override fun Content() {
        val model = rememberScreenModel { MapScreenModel() }

        val markers: List<MapMarker> by model.markers.collectAsState()
        val selectedMarker: MapMarker? by model.selectedMarker.collectAsState()
        val activeFilter: MapFilter by model.activeFilter.collectAsState()
        val searchQuery: String by model.searchQuery.collectAsState()
        val suggestions: List<SearchSuggestion> by model.suggestions.collectAsState()
        val isCreateMode: Boolean by model.isCreateMode.collectAsState()
        val createType by model.createType.collectAsState()
        val createDescription: String by model.createDescription.collectAsState()
        val createAddress: String by model.createAddress.collectAsState()
        val privacyConsent: Boolean by model.privacyConsent.collectAsState()
        val cameraPosition: CameraPosition by model.cameraPosition.collectAsState()

        val problems: List<Problem> by InMemoryRepository.problems.collectAsState()
        val events: List<CleanupEvent> by InMemoryRepository.events.collectAsState()

        val selectedProblem: Problem? = selectedMarker?.let { marker: MapMarker ->
            if (marker.type != MapMarkerType.EVENT) problems.find { it.id == marker.id } else null
        }
        val selectedEvent: CleanupEvent? = selectedMarker?.let { marker: MapMarker ->
            if (marker.type == MapMarkerType.EVENT) events.find { it.id == marker.id } else null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            YandexMapView(
                modifier = Modifier.fillMaxSize(),
                cameraPosition = cameraPosition,
                markers = markers,
                onMarkerClick = { model.selectMarker(it) },
                onMapTap = { lat, lon ->
                    model.clearSelection()
                    model.onMapTap(lat, lon)
                },
            )

            MapSearchBar(
                query = searchQuery,
                onQueryChange = { model.updateSearchQuery(it) },
                suggestions = suggestions,
                onSuggestionClick = { model.selectSuggestion(it) },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            MapFilterChips(
                activeFilter = activeFilter,
                onFilterClick = { model.setFilter(it) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )

            MapLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 120.dp, end = 16.dp),
            )

            MapFabGroup(
                onCreateClick = { model.openCreateMode() },
                onLocationClick = {
                    model.moveCameraTo(43.585, 39.723, 14f)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )

            if (selectedProblem != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ProblemBottomSheet(
                        problem = selectedProblem,
                        onVerify = { model.verifyProblem(selectedProblem.id) },
                        onVoteYes = { model.voteProblem(selectedProblem.id, true) },
                        onVoteNo = { model.voteProblem(selectedProblem.id, false) },
                        onDismiss = { model.clearSelection() },
                    )
                }
            }

            if (selectedEvent != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    EventBottomSheet(
                        event = selectedEvent,
                        onJoin = { InMemoryRepository.joinEvent(selectedEvent.id) },
                        onDismiss = { model.clearSelection() },
                    )
                }
            }

            AnimatedVisibility(
                visible = isCreateMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                CreateMarkerPanel(
                    selectedType = createType,
                    onTypeSelect = { model.setCreateType(it) },
                    description = createDescription,
                    onDescriptionChange = { model.setCreateDescription(it) },
                    address = createAddress,
                    privacyConsent = privacyConsent,
                    onPrivacyConsentChange = { model.setPrivacyConsent(it) },
                    onSubmit = { model.submitProblem() },
                    onClose = { model.closeCreateMode() },
                )
            }
        }
    }
}
