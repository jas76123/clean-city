package com.example.cleancity.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private var appContext: Context? = null

fun initPlatform(context: Context) {
    appContext = context.applicationContext
}

actual fun openUrl(url: String) {
    val context = appContext ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

actual fun sendEmail(to: String, subject: String, body: String) {
    val context = appContext ?: return
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
