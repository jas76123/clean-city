package com.example.cleancity.ui.feature.map.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.ui.feature.map.YandexMapHost
import org.koin.core.parameter.parametersOf

data class MapPickerScreen(
    val initialLat: Double? = null,
    val initialLon: Double? = null,
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<MapPickerScreenModel> {
            parametersOf(initialLat, initialLon)
        }
        val state by model.state.collectAsState()

        // cameraPosition строится один раз и не зависит от state — иначе onCameraMoved →
        // update(state) → recomposition → LaunchedEffect(cameraPosition) в YandexMapHost снова
        // двигает карту → CameraListener.finished → бесконечный цикл и pin «дёргается».
        val cameraPosition = remember(initialLat, initialLon) {
            CameraPosition(
                latitude = initialLat ?: SochiDefaults.CENTER.latitude,
                longitude = initialLon ?: SochiDefaults.CENTER.longitude,
                zoom = if (initialLat != null && initialLon != null) 16f else 12f,
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Выбрать адрес") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = {
                                if (model.confirm()) navigator.pop()
                            },
                            enabled = state.address != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Подтвердить адрес", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                YandexMapHost(
                    cameraPosition = cameraPosition,
                    markers = emptyList(),
                    onCameraMoved = model::onCameraMoved,
                    onMarkerClick = {},
                    onClusterTap = { _, _ -> },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AddressPill(
                        state = state,
                        modifier = Modifier.offset(y = (-96).dp).widthIn(max = 320.dp),
                    )
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Метка",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp).offset(y = (-24).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressPill(
    state: MapPickerScreenModel.UiState,
    modifier: Modifier = Modifier,
) {
    val text: String? = when {
        state.isResolving -> null
        state.address != null -> state.address
        else -> "Адрес не определён, подвиньте карту"
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Определяем адрес…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}
