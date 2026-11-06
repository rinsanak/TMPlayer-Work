package com.tmplayer.data

import org.json.JSONArray
import org.json.JSONObject

/** Per-key last-change records. A missing value is an explicit deletion, not a missing snapshot. */
object AccountSyncDoc {
    data class Entry(val value: Any?, val at: Long, val writer: String)
    fun same(a: Any?, b: Any?): Boolean = encodeValue(a).toString() == encodeValue(b).toString()
    fun merge(a: Map<String, Entry>, b: Map<String, Entry>): Map<String, Entry> = (a.keys + b.keys).associateWith { key ->
        val x = a[key]; val y = b[key]
        when {
            x == null -> y!!
            y == null -> x
            y.at > x.at || (y.at == x.at && y.writer > x.writer) -> y
            else -> x
        }
    }
    fun encode(entries: Map<String, Entry>): String = JSONObject().apply {
        entries.toSortedMap().forEach { (key, e) -> put(key, JSONObject().put("v", encodeValue(e.value)).put("at", e.at).put("by", e.writer)) }
    }.toString()
    fun decode(text: String): Map<String, Entry> {
        if (text.length > 2_000_000) return emptyMap()
        return runCatching {
            val root = JSONObject(text)
            if (root.length() > 20_000) return emptyMap()
            root.keys().asSequence().mapNotNull { key ->
                val e = root.optJSONObject(key) ?: return@mapNotNull null
                val at = e.optLong("at", -1); val writer = e.optString("by")
                if (at < 0 || writer.length !in 1..64 || key.length > 256) return@mapNotNull null
                val wrapped = e.optJSONObject("v") ?: return@mapNotNull null
                key to Entry(decodeValue(wrapped), at, writer)
            }.toMap()
        }.getOrDefault(emptyMap())
    }
    private fun encodeValue(value: Any?): JSONObject = JSONObject().apply {
        when (value) {
            null -> put("t", "null")
            is Boolean -> { put("t", "bool"); put("v", value) }
            is Long, is Int -> { put("t", "long"); put("v", value) }
            is Float, is Double -> { put("t", "float"); put("v", value) }
            is Set<*> -> { put("t", "set"); put("v", JSONArray(value.map { it.toString() }.sorted())) }
            is String -> { put("t", "text"); put("v", value) }
            else -> throw IllegalArgumentException("Unsupported setting type")
        }
    }
    private fun decodeValue(v: JSONObject): Any? = when (v.getString("t")) {
        "null" -> null
        "bool" -> v.getBoolean("v")
        "long" -> v.getLong("v")
        "float" -> v.getDouble("v").toFloat().takeIf { it.isFinite() } ?: throw IllegalArgumentException("Invalid number")
        "text" -> v.getString("v")
        "set" -> v.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> throw IllegalArgumentException("Unknown setting type")
    }
}
