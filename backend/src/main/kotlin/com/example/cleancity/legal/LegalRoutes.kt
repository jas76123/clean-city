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
    // [text](url) — block javascript: scheme, escape " in URL
    result = Regex("""\[(.+?)]\((.+?)\)""").replace(result) { m ->
        val rawUrl = m.groupValues[2].trim()
        if (rawUrl.lowercase().startsWith("javascript:")) {
            "<span>${m.groupValues[1]}</span>"
        } else {
            val safeUrl = rawUrl.replace("\"", "&quot;")
            "<a href=\"$safeUrl\">${m.groupValues[1]}</a>"
        }
    }
    return result
}

/** Returns true if a table row's cells are all separator dashes (e.g. |---|:---:|). */
private fun isTableSeparator(cells: List<String>): Boolean =
    cells.isNotEmpty() && cells.all { it.trim().matches(Regex(""":?-+:?""")) }

private fun renderMarkdown(md: String): String {
    val lines = md.lines()
    val sb = StringBuilder()

    var inList = false
    var inTable = false
    var tableIsFirstRow = true
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

    fun closeTable() {
        if (inTable) {
            sb.append("</tbody></table>\n")
            inTable = false
            tableIsFirstRow = true
        }
    }

    fun splitTableRow(line: String): List<String> =
        line.trim().trimStart('|').trimEnd('|').split("|").map { it.trim() }

    for (line in lines) {
        val trimmed = line.trim()
        when {
            // Headings
            line.startsWith("### ") -> {
                flushParagraph(); closeList(); closeTable()
                sb.append("<h3>${applyInline(line.removePrefix("### ").escapeHtml())}</h3>\n")
            }
            line.startsWith("## ") -> {
                flushParagraph(); closeList(); closeTable()
                sb.append("<h2>${applyInline(line.removePrefix("## ").escapeHtml())}</h2>\n")
            }
            line.startsWith("# ") -> {
                flushParagraph(); closeList(); closeTable()
                sb.append("<h1>${applyInline(line.removePrefix("# ").escapeHtml())}</h1>\n")
            }
            // Horizontal rule: line is exactly --- or ***
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph(); closeList(); closeTable()
                sb.append("<hr>\n")
            }
            // Table row
            trimmed.startsWith("|") -> {
                flushParagraph(); closeList()
                val cells = splitTableRow(trimmed)
                if (isTableSeparator(cells)) {
                    // Skip separator row — it's already been written as <thead>
                } else if (tableIsFirstRow) {
                    // First data row → open table and write as <thead>
                    sb.append("<table><thead><tr>")
                    cells.forEach { sb.append("<th>${applyInline(it.escapeHtml())}</th>") }
                    sb.append("</tr></thead><tbody>\n")
                    inTable = true
                    tableIsFirstRow = false
                } else {
                    sb.append("<tr>")
                    cells.forEach { sb.append("<td>${applyInline(it.escapeHtml())}</td>") }
                    sb.append("</tr>\n")
                }
            }
            // List items (- or *)
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph(); closeTable()
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
                closeTable()
            }
            // Regular text line
            else -> {
                closeList(); closeTable()
                pendingParagraphLines.add(applyInline(line.escapeHtml()))
            }
        }
    }

    flushParagraph()
    closeList()
    closeTable()

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
  table{border-collapse:collapse;width:100%;margin:8px 0}th,td{border:1px solid #d1d5db;padding:6px 8px;text-align:left;font-size:0.92rem}
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
        call.response.header(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; img-src data:; frame-ancestors 'none'; base-uri 'none'"
        )
        call.respondText(html, ContentType.Text.Html)
    }

    get("/legal/terms") {
        val md = loadResource("legal/terms-of-service.md")
            ?: return@get call.respondText("Документ не найден", status = HttpStatusCode.NotFound)
        val html = wrapHtml("Пользовательское соглашение", renderMarkdown(md))
        call.response.header(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; img-src data:; frame-ancestors 'none'; base-uri 'none'"
        )
        call.respondText(html, ContentType.Text.Html)
    }
}
