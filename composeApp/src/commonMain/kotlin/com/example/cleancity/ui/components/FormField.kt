package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.Gray200
import com.example.cleancity.ui.theme.Gray300
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Red

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Gray600,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green400,
                unfocusedBorderColor = Gray200,
                errorBorderColor = Red,
                focusedTextColor = Gray900,
                unfocusedTextColor = Gray900,
                cursorColor = Green400,
                focusedPlaceholderColor = Gray300,
                unfocusedPlaceholderColor = Gray300,
            ),
            isError = error != null,
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = Red)
        } else if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
    }
}
