package com.example.cleancity.platform

expect fun randomUUID(): String
expect fun openUrl(url: String)
expect fun sendEmail(to: String, subject: String, body: String)
expect fun currentTimeMillis(): Long
