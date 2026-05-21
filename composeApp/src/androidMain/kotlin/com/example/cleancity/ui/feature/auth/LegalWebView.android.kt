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

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        view?.loadDataWithBaseURL(
                            null,
                            """
                            <html><body style="font-family:sans-serif;padding:32px;color:#4A6055;text-align:center">
                            <h3>Документ временно недоступен</h3>
                            <p>Не удалось загрузить страницу. Проверьте интернет-соединение
                            и попробуйте позже.</p>
                            </body></html>
                            """.trimIndent(),
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                }
            }
            loadUrl(url)
        }
    })
}

@Composable
actual fun currentApiBase(): String = BuildConfig.API_BASE_URL
