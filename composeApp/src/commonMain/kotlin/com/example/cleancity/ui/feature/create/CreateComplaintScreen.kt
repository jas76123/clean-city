package com.example.cleancity.ui.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.domain.location.PermissionStatus
import com.example.cleancity.domain.location.rememberLocationPermission
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.DuplicateCandidateResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.map.components.emoji
import kotlinx.coroutines.flow.collectLatest

class CreateComplaintScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<CreateComplaintScreenModel>()
        val state by model.state.collectAsState()
        val permission = rememberLocationPermission()

        // Запрашиваем GPS-разрешение один раз при первом отображении экрана.
        LaunchedEffect(Unit) {
            when (permission.status) {
                PermissionStatus.Granted -> model.onLocationPermissionGranted()
                PermissionStatus.NotRequested -> permission.launchRequest()
                PermissionStatus.Denied -> model.onLocationPermissionDenied()
            }
        }
        // Реагируем на смену статуса permission после launchRequest().
        LaunchedEffect(permission.status) {
            when (permission.status) {
                PermissionStatus.Granted -> model.onLocationPermissionGranted()
                PermissionStatus.Denied -> model.onLocationPermissionDenied()
                PermissionStatus.NotRequested -> {} // ждём действия пользователя
            }
        }

        LaunchedEffect(Unit) {
            model.events.collectLatest { event ->
                when (event) {
                    is CreateComplaintEvent.CreatedSuccessfully -> {
                        navigator.replace(ComplaintDetailScreen(event.complaintId))
                    }
                    is CreateComplaintEvent.OpenExistingComplaint -> {
                        navigator.replace(ComplaintDetailScreen(event.complaintId))
                    }
                }
            }
        }

        var cameraDeniedAlert by remember { mutableStateOf(false) }
        val photoPicker = rememberPhotoPickerLauncher(
            onPhotosPicked = model::onPhotosAdded,
            onCameraPermissionDenied = { cameraDeniedAlert = true },
        )
        var pickerSheetOpen by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Новая жалоба") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
            bottomBar = {
                SubmitBar(
                    state = state,
                    onSubmit = model::submit,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PhotoBlock(
                    photos = state.photos,
                    onAddRequested = { pickerSheetOpen = true },
                    onRemove = model::onPhotoRemoved,
                )

                if (state.duplicates.isNotEmpty()) {
                    DuplicateWarning(
                        items = state.duplicates,
                        onVote = model::voteForDuplicate,
                    )
                }

                CategorySection(
                    selected = state.category,
                    query = state.categoryQuery,
                    onQueryChange = model::onCategoryQueryChanged,
                    onSelect = model::onCategorySelected,
                )

                AddressSection(
                    address = state.address,
                    onAddressChange = model::onAddressChanged,
                    locationStatus = state.locationStatus,
                )

                DescriptionSection(
                    description = state.description,
                    onChange = model::onDescriptionChanged,
                )
            }
        }

        if (pickerSheetOpen) {
            PhotoSourceSheet(
                remainingSlots = MAX_PHOTOS - state.photos.size,
                onPick = { source ->
                    pickerSheetOpen = false
                    photoPicker.launch(source, MAX_PHOTOS - state.photos.size)
                },
                onDismiss = { pickerSheetOpen = false },
            )
        }

        state.submitError?.let { msg ->
            AlertDialog(
                onDismissRequest = model::clearError,
                title = { Text("Не получилось") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = model::clearError) { Text("Ок") }
                },
            )
        }

        if (cameraDeniedAlert) {
            AlertDialog(
                onDismissRequest = { cameraDeniedAlert = false },
                title = { Text("Нужно разрешение на камеру") },
                text = {
                    Text(
                        "Чтобы сделать фото, разреши приложению доступ к камере в системных " +
                            "настройках, либо выбери фото из галереи.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { cameraDeniedAlert = false }) { Text("Понятно") }
                },
            )
        }
    }
}

// ---------- Photo block ----------

@Composable
private fun PhotoBlock(
    photos: List<com.example.cleancity.domain.photo.PhotoBytes>,
    onAddRequested: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (photos.isEmpty()) {
            EmptyPhotoBlock(onClick = onAddRequested)
        } else {
            PhotoThumbnailRow(photos = photos, onAdd = onAddRequested, onRemove = onRemove)
        }
    }
}

@Composable
private fun EmptyPhotoBlock(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Сделать фото или загрузить",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Можно до 5 фотографий",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun PhotoThumbnailRow(
    photos: List<com.example.cleancity.domain.photo.PhotoBytes>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        photos.forEachIndexed { index, photo ->
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.TopEnd,
            ) {
                coil3.compose.AsyncImage(
                    model = photo.bytes,
                    contentDescription = "Фото ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Удалить фото",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (photos.size < MAX_PHOTOS) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = "Добавить ещё",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    remainingSlots: Int,
    onPick: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Добавить фото",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Осталось слотов: $remainingSlots",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            SheetRow(icon = Icons.Default.AddAPhoto, label = "Сделать фото") { onPick(PhotoSource.CAMERA) }
            SheetRow(icon = Icons.Default.PhotoLibrary, label = "Выбрать из галереи") { onPick(PhotoSource.GALLERY) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------- Duplicate warning ----------

@Composable
private fun DuplicateWarning(
    items: List<DuplicateCandidateResponse>,
    onVote: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column {
                    Text(
                        "Возможно, эта проблема уже есть",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Нашли ${items.size} жалоб(ы) рядом — поддержите вместо новой",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items.take(3).forEach { item ->
                DuplicateRow(item = item, onVote = { onVote(item.id) })
            }
        }
    }
}

@Composable
private fun DuplicateRow(item: DuplicateCandidateResponse, onVote: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.category.emoji(), fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "${item.votesCount} голосов · ${item.distanceMeters}м · ${item.status.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
            Button(
                onClick = onVote,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("+1 голос", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun ComplaintStatus.label(): String = when (this) {
    ComplaintStatus.NEW -> "Новая"
    ComplaintStatus.IN_PROGRESS -> "В работе"
    ComplaintStatus.RESOLVED -> "Решена"
    ComplaintStatus.REJECTED -> "Отклонена"
    ComplaintStatus.DUPLICATE -> "Дубликат"
}

// ---------- Category section ----------

@Composable
private fun CategorySection(
    selected: ProblemCategory?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (ProblemCategory) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Тип проблемы")
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Найти категорию…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(),
            )
            val filtered = filterCategories(query)
            // 3 столбца × 6 строк = 18 категорий — вручную грид через Column/Row,
            // т.к. LazyVerticalGrid не работает внутри verticalScroll-родителя.
            filtered.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        CategoryCard(
                            category = cat,
                            selected = cat == selected,
                            onClick = { onSelect(cat) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // дозабиваем пустыми ячейками если ряд неполный
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    "Ничего не нашли — попробуй другое слово",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun filterCategories(query: String): List<ProblemCategory> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return ProblemCategory.entries
    return ProblemCategory.entries.filter { it.localizedLabel.lowercase().contains(q) }
}

@Composable
private fun CategoryCard(
    category: ProblemCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .aspectRatio(1.05f)
            .border(width = if (selected) 2.dp else 1.dp, color = border, shape = RoundedCornerShape(14.dp))
            .background(bg, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(category.emoji(), fontSize = 22.sp)
            Text(
                category.localizedLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

// ---------- Address section ----------

@Composable
private fun AddressSection(
    address: String,
    onAddressChange: (String) -> Unit,
    locationStatus: LocationStatus,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Адрес")
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                placeholder = { Text("ул. Транспортная, 14") },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = locationStatus.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun LocationStatus.hint(): String = when (this) {
    LocationStatus.Idle -> "Определяем геопозицию…"
    LocationStatus.Requesting -> "Получаем GPS…"
    LocationStatus.Ready -> "Геопозиция определена автоматически"
    LocationStatus.PermissionDenied -> "Нет разрешения на GPS — введите адрес вручную"
    is LocationStatus.Failed -> "GPS недоступен — введите адрес вручную"
}

// ---------- Description ----------

@Composable
private fun DescriptionSection(description: String, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Описание")
            OutlinedTextField(
                value = description,
                onValueChange = onChange,
                placeholder = { Text("Опишите проблему как можно подробнее…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${description.length} / $MAX_DESCRIPTION_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

// ---------- Submit bar ----------

@Composable
private fun SubmitBar(state: CreateComplaintUiState, onSubmit: () -> Unit) {
    Surface(tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (state.isSubmitting) "Отправляем…" else "Отправить жалобу",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = FontWeight.SemiBold,
    )
}
