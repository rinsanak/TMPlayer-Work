package com.tmplayer.data

import java.io.File
import java.io.IOException
import java.util.Locale

/** Durable per-record numbering across rotated parts, with explicit storage limits. */
class DiagnosticJournal(private val directory: File, val serial: Long, private val partBytes: Long = 8L * 1024 * 1024) {
    private var sequence = 0L
    private var part = 0
    private var bytes = 0L
    private var total = 0L
    private var closed = false
    private var checkedSpaceAt = 0L
    private var writer: java.io.BufferedWriter? = null
    val files = mutableListOf<File>()
    init { if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create diagnostic directory") }
    @Synchronized fun record(source: String, message: String) {
        if (closed) return
        val checkSpace = System.currentTimeMillis() - checkedSpaceAt > 1000
        if (checkSpace) checkedSpaceAt = System.currentTimeMillis()
        if (total >= 128L * 1024 * 1024 || (checkSpace && directory.usableSpace < 10L * 1024 * 1024)) {
            throw IOException("Diagnostic storage limit reached; recorded parts are retained")
        }
        for (line in message.lineSequence()) {
            if (writer == null || bytes >= partBytes) {
                writer?.flush(); writer?.close()
                val file = File(directory, "part-${(++part).toString().padStart(4, '0')}.txt")
                files += file; writer = file.bufferedWriter(); bytes = 0
            }
            val text = String.format(Locale.ROOT, "#%08d utcMs=%d %s %s\n", ++sequence, System.currentTimeMillis(), source, DiagnosticRedactor.scrub(line))
            writer?.write(text)
            val count = text.toByteArray().size
            bytes += count; total += count
        }
    }
    @Synchronized fun flush() { writer?.flush() }
    @Synchronized fun close() { if (!closed) { writer?.flush(); writer?.close(); closed = true } }
}
object DiagnosticRedactor {
    private val credentials = Regex("""(?i)\b(authorization|api[_-]?(?:key|hash)|access[_-]?token|refresh[_-]?token|password|secret|cookie|auth[_-]?key|phone[_-]?number|verification[_-]?code)\b["']?\s*[:=]\s*["']?[^"'\s,;}]+""")
    private val bearer = Regex("""(?i)\bBearer\s+[^\s,;]+""")
    private val url = Regex("""(?i)(?:https?|tg)://[^\s]+""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""\+\d[\d ()-]{7,}\d""")
    fun scrub(line: String): String = phone.replace(email.replace(url.replace(credentials.replace(bearer.replace(line, "Bearer [redacted]")) {
        it.groupValues[1] + "=[redacted]"
    }, "[url]"), "[email]"), "[phone]")
}
