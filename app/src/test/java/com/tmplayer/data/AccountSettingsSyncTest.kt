package com.tmplayer.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountSettingsSyncTest {
    @Test fun `cloud preferences exclude device security and apply deletions without importing private paths`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "account-${UUID.randomUUID()}.preferences_pb")
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            store.restoreEntries { mapOf("theme_choice" to "dark", "app_pin" to "local-pin", "download_root" to "/local/storage", "favorite_chats" to setOf("123")) }
            val snapshot = AccountSettingsSync.values(context, store)
            assertEquals("dark", snapshot["s:theme_choice"])
            assertEquals(setOf("123"), snapshot["s:favorite_chats"])
            assertFalse(snapshot.containsKey("s:app_pin"))
            assertFalse(snapshot.containsKey("s:download_root"))
            AccountSettingsSync.apply(context, store, mapOf("s:theme_choice" to null, "s:app_pin" to "remote-pin", "s:download_root" to "/remote/storage", "s:dialog_boost" to true))
            val restored = store.rawEntries()
            assertFalse(restored.containsKey("theme_choice"))
            assertEquals("local-pin", restored["app_pin"])
            assertEquals("/local/storage", restored["download_root"])
            assertEquals(true, restored["dialog_boost"])
        } finally { scope.cancel() }
    }
    @Test fun `local sync journal encrypts credentials and authenticates ciphertext`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val secret = "{\"api_key\":\"service-secret\"}"
        val encrypted = AccountSyncVault.encrypt(secret, key)
        assertFalse(encrypted.contains("service-secret"))
        assertEquals(secret, AccountSyncVault.decrypt(encrypted, key))
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(runCatching { AccountSyncVault.decrypt(encrypted, other) }.isFailure)
    }
}
