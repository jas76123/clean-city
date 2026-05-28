package com.example.cleancity.ui.feature.shell

import androidx.compose.runtime.Composable

/**
 * Платформенный «гейт» для запроса разрешения на показ push.
 * Android 13+ — показывает rationale-диалог + системный запрос ONE-TIME.
 * Pre-13 / iOS — no-op (permission даётся автоматически или функционал
 * недоступен).
 *
 * Вызывается из MainShellScreen после успешной аутентификации.
 */
@Composable
expect fun NotificationPermissionGate(enabled: Boolean)
