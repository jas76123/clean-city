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
import com.example.cleancity.ui.map.components.*

class MapScreen : Screen {

    @Composable
    override fun Content() {
        val model = rememberScreenModel { MapScreenModel() }

        val markers by model.markers.collectAsState()
        val selectedMarker by model.selectedMarker.collectAsState()
        val activeFilter by model.activeFilter.collectAsState()
        val searchQuery by model.searchQuery.collectAsState()
        val suggestions by model.suggestions.collectAsState()
        val isCreateMode by model.isCreateMode.collectAsState()
        val createType by model.createType.collectAsState()
        val createDescription by model.createDescription.collectAsState()
        val createAddress by model.createAddress.collectAsState()
        val privacyConsent by model.privacyConsent.collectAsState()
        val cameraPosition by model.cameraPosition.collectAsState()

        val problems by InMemoryRepository.problems.collectAsState()
        val events by InMemoryRepository.events.collectAsState()

        val selectedProblem = selectedMarker?.let { marker ->
            if (marker.type != MapMarkerType.EVENT) problems.find { it.id == marker.id } else null
        }
        val selectedEvent = selectedMarker?.let { marker ->
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

            AnimatedVisibility(
                visible = selectedProblem != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedProblem?.let { problem ->
                    ProblemBottomSheet(
                        problem = problem,
                        onVerify = { model.verifyProblem(problem.id) },
                        onVoteYes = { model.voteProblem(problem.id, true) },
                        onVoteNo = { model.voteProblem(problem.id, false) },
                        onDismiss = { model.clearSelection() },
                    )
                }
            }

            AnimatedVisibility(
                visible = selectedEvent != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedEvent?.let { event ->
                    EventBottomSheet(
                        event = event,
                        onJoin = { InMemoryRepository.joinEvent(event.id) },
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
