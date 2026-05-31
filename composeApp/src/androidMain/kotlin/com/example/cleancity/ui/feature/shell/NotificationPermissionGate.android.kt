package com.example.cleancity.ui.feature.shell

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val PREFS = "cleancity_notif_prefs"
private const val KEY_ASKED = "notif_permission_asked"

@Composable
actual fun NotificationPermissionGate(enabled: Boolean) {
    if (!enabled) return
    if (Build.VERSION.SDK_INT < 33) return   // Android <13 — permission неявный

    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ответ юзера не интересует — флаг уже выставлен */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val asked = prefs.getBoolean(KEY_ASKED, false)
        if (!granted && !asked) showRationale = true
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
            },
            title = { Text("Уведомления") },
            text = {
                Text(
                    "Чтобы вы не пропустили важные объявления от муниципальных служб, " +
                        "разрешите приложению показывать уведомления.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                }) { Text("Не сейчас") }
            },
        )
    }
}
