package com.example.cleancity.ui.feature.auth

import androidx.compose.runtime.Composable

@Composable
expect fun LegalWebView(url: String)

@Composable
expect fun currentApiBase(): String
