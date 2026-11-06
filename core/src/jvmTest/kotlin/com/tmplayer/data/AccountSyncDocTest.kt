package com.tmplayer.data

import org.junit.Assert.*
import org.junit.Test

class AccountSyncDocTest {
    private fun entry(v: Any?, at: Long, writer: String) = AccountSyncDoc.Entry(v, at, writer)
    @Test fun `concurrent devices preserve distinct edits and converge on same-field conflicts`() {
        val phone = mapOf("s:theme" to entry("dark", 20, "phone"), "s:dialog_boost" to entry(true, 30, "phone"))
        val tv = mapOf("s:language" to entry("ml", 10, "tv"), "s:theme" to entry("light", 40, "tv"))
        val merged = AccountSyncDoc.merge(phone, tv)
        assertEquals("light", merged.getValue("s:theme").value)
        assertEquals(true, merged.getValue("s:dialog_boost").value)
        assertEquals("ml", merged.getValue("s:language").value)
        assertEquals(merged, AccountSyncDoc.merge(tv, phone))
        assertEquals(merged, AccountSyncDoc.merge(merged, phone))
    }
    @Test fun `credential deletion survives serialization and an older offline device`() {
        val deletion = mapOf("g:key" to entry(null, 40, "phone"))
        val old = mapOf("g:key" to entry("old-service-key", 20, "tv"))
        val result = AccountSyncDoc.merge(AccountSyncDoc.decode(AccountSyncDoc.encode(deletion)), old)
        assertNull(result.getValue("g:key").value)
    }
    @Test fun `native preference types and clock ties are preserved`() {
        val values = mapOf("set" to entry(setOf("1", "2"), 10, "phone"), "float" to entry(1.25f, 11, "tv"), "long" to entry(9000L, 12, "tv"), "bool" to entry(false, 13, "phone"))
        assertEquals(values, AccountSyncDoc.decode(AccountSyncDoc.encode(values)))
        val a = mapOf("x" to entry(1L, 10, "a")); val b = mapOf("x" to entry(2L, 10, "b"))
        assertEquals(AccountSyncDoc.merge(a, b), AccountSyncDoc.merge(b, a))
    }
    @Test fun `truncated and oversized documents cannot import values`() {
        assertTrue(AccountSyncDoc.decode("{\"g:key\":").isEmpty())
        assertTrue(AccountSyncDoc.decode("x".repeat(2_000_001)).isEmpty())
    }
}
