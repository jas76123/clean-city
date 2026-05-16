package com.example.cleancity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "cleancity") return
        val token = uri.getQueryParameter("token") ?: return
        when (uri.host) {
            "verify" -> DeepLinkBus.emit(DeepLink.Verify(token))
            "reset" -> DeepLinkBus.emit(DeepLink.Reset(token))
        }
    }
}
