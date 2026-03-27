package com.example.cleancity.platform

import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.Foundation.NSURL

actual fun randomUUID(): String = NSUUID().UUIDString()
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun sendEmail(to: String, subject: String, body: String) {
    val encodedSubject = subject.replace(" ", "%20").replace("\n", "%0A")
    val encodedBody = body.replace(" ", "%20").replace("\n", "%0A").replace("&", "%26")
    val mailtoUrl = "mailto:$to?subject=$encodedSubject&body=$encodedBody"
    openUrl(mailtoUrl)
}
