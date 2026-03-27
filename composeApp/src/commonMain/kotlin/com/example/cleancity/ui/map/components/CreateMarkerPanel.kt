package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.ProblemType
import com.example.cleancity.ui.theme.*

@Composable
fun CreateMarkerPanel(
    selectedType: ProblemType?,
    onTypeSelect: (ProblemType) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    address: String,
    privacyConsent: Boolean,
    onPrivacyConsentChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Новая метка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900,
                )
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Gray100,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("✕", fontSize = 14.sp, color = Gray600)
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Type selector 2x2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TypeOption(ProblemType.DUMP, selectedType == ProblemType.DUMP, onTypeSelect, Modifier.weight(1f))
                        TypeOption(ProblemType.HOLES, selectedType == ProblemType.HOLES, onTypeSelect, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TypeOption(ProblemType.LIGHTING, selectedType == ProblemType.LIGHTING, onTypeSelect, Modifier.weight(1f))
                        TypeOption(ProblemType.GREENERY, selectedType == ProblemType.GREENERY, onTypeSelect, Modifier.weight(1f))
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Опишите проблему...", color = Gray300) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green400,
                        unfocusedBorderColor = Gray200,
                    ),
                )

                // Address display
                if (address.isNotBlank()) {
                    Text("📍 $address", style = MaterialTheme.typography.bodySmall, color = Gray500)
                } else {
                    Text("Тапните по карте для выбора места", style = MaterialTheme.typography.bodySmall, color = Gray400)
                }

                // Photo upload placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(2.dp, Green300, RoundedCornerShape(16.dp))
                        .background(Green50, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 24.sp)
                        Text("Добавить фото", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Green700)
                    }
                }

                // Privacy consent
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = privacyConsent,
                        onCheckedChange = onPrivacyConsentChange,
                        colors = CheckboxDefaults.colors(checkedColor = Green600),
                    )
                    Text(
                        "Я соглашаюсь с Политикой конфиденциальности и даю согласие на обработку персональных данных по ФЗ-152",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray500,
                        lineHeight = 16.sp,
                    )
                }

                // Submit button
                Button(
                    onClick = onSubmit,
                    enabled = selectedType != null && privacyConsent && address.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green900),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Отправить", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TypeOption(
    type: ProblemType,
    selected: Boolean,
    onSelect: (ProblemType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Green500 else Gray200
    val bgColor = if (selected) Green50 else Color.White

    Surface(
        onClick = { onSelect(type) },
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(type.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                type.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Green700 else Gray700,
            )
        }
    }
}
