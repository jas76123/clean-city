package com.example.cleancity.ui.feature.auth

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cleancity.BuildConfig

@Composable
actual fun LegalWebView(url: String) {
    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val allowedHost = Uri.parse(BuildConfig.API_BASE_URL).host
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val host = request?.url?.host ?: return true
                    return host != allowedHost
                }
            }
            loadUrl(url)
        }
    })
}

@Composable
actual fun currentApiBase(): String = BuildConfig.API_BASE_URL
