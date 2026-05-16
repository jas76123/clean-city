package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700

private const val TAG_TERMS = "TERMS"
private const val TAG_PRIVACY = "PRIVACY"

@Composable
fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = buildAnnotatedString {
        append("Я принимаю ")
        pushStringAnnotation(tag = TAG_TERMS, annotation = "terms")
        withStyle(SpanStyle(color = Green600, fontWeight = FontWeight.SemiBold)) {
            append("Условия")
        }
        pop()
        append(" и ")
        pushStringAnnotation(tag = TAG_PRIVACY, annotation = "privacy")
        withStyle(SpanStyle(color = Green600, fontWeight = FontWeight.SemiBold)) {
            append("Политику обработки данных")
        }
        pop()
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Green700,
                uncheckedColor = Gray600,
                checkmarkColor = Accent,
            ),
        )
        ClickableText(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = Gray600),
            onClick = { offset ->
                text.getStringAnnotations(TAG_TERMS, offset, offset).firstOrNull()?.let { onTermsClick() }
                text.getStringAnnotations(TAG_PRIVACY, offset, offset).firstOrNull()?.let { onPrivacyClick() }
            },
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}
