package com.example.cleancity.legal

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// ---------------------------------------------------------------------------
// Minimal Markdown → HTML renderer (no external libraries)
// ---------------------------------------------------------------------------

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun applyInline(text: String): String {
    // text is already HTML-escaped; apply **bold** and [link](url) transforms
    var result = text
    // **bold**
    result = Regex("""\*\*(.+?)\*\*""").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
    // [text](url)
    result = Regex("""\[(.+?)]\((.+?)\)""").replace(result) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
    return result
}

private fun renderMarkdown(md: String): String {
    val lines = md.lines()
    val sb = StringBuilder()

    var inList = false
    var pendingParagraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (pendingParagraphLines.isEmpty()) return
        sb.append("<p>")
        sb.append(pendingParagraphLines.joinToString("<br>"))
        sb.append("</p>\n")
        pendingParagraphLines = mutableListOf()
    }

    fun closeList() {
        if (inList) {
            sb.append("</ul>\n")
            inList = false
        }
    }

    for (line in lines) {
        when {
            // Headings
            line.startsWith("### ") -> {
                flushParagraph(); closeList()
                sb.append("<h3>${applyInline(line.removePrefix("### ").escapeHtml())}</h3>\n")
            }
            line.startsWith("## ") -> {
                flushParagraph(); closeList()
                sb.append("<h2>${applyInline(line.removePrefix("## ").escapeHtml())}</h2>\n")
            }
            line.startsWith("# ") -> {
                flushParagraph(); closeList()
                sb.append("<h1>${applyInline(line.removePrefix("# ").escapeHtml())}</h1>\n")
            }
            // List items (- or *)
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                if (!inList) {
                    sb.append("<ul>\n")
                    inList = true
                }
                val itemText = if (line.startsWith("- ")) line.removePrefix("- ") else line.removePrefix("* ")
                sb.append("<li>${applyInline(itemText.escapeHtml())}</li>\n")
            }
            // Blank line
            line.isBlank() -> {
                flushParagraph()
                closeList()
            }
            // Regular text line
            else -> {
                closeList()
                pendingParagraphLines.add(applyInline(line.escapeHtml()))
            }
        }
    }

    flushParagraph()
    closeList()

    return sb.toString()
}

private fun wrapHtml(title: String, body: String): String = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
  body { font-family: -apple-system, Roboto, sans-serif; line-height: 1.55; color: #1f2937; padding: 16px; max-width: 720px; margin: 0 auto; }
  h1 { font-size: 1.4rem; } h2 { font-size: 1.18rem; } h3 { font-size: 1.05rem; }
  a { color: #2563eb; } ul { padding-left: 1.2rem; }
</style>
</head>
<body>
$body
</body>
</html>
"""

private fun loadResource(path: String): String? =
    object {}.javaClass.classLoader.getResource(path)?.readText()

// ---------------------------------------------------------------------------
// Ktor route extension — PUBLIC, no auth
// ---------------------------------------------------------------------------

fun Route.legalRoutes() {
    get("/legal/privacy") {
        val md = loadResource("legal/privacy-policy.md")
            ?: return@get call.respondText("Документ не найден", status = HttpStatusCode.NotFound)
        val html = wrapHtml("Политика конфиденциальности", renderMarkdown(md))
        call.respondText(html, ContentType.Text.Html)
    }

    get("/legal/terms") {
        val md = loadResource("legal/terms-of-service.md")
            ?: return@get call.respondText("Документ не найден", status = HttpStatusCode.NotFound)
        val html = wrapHtml("Пользовательское соглашение", renderMarkdown(md))
        call.respondText(html, ContentType.Text.Html)
    }
}
