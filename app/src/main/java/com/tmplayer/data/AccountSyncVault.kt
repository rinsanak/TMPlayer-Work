package com.tmplayer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local encrypted storage only. Each device uses its own Android Keystore key. */
internal object AccountSyncVault {
    private fun key(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(file: File, alias: String): String {
        if (!file.exists()) return ""
        check(file.length() <= 4_000_000) { "Sync state is too large" }
        return decrypt(file.readText(), key(alias))
    }
    internal fun decrypt(text: String, key: SecretKey): String {
        val parts = text.lines(); check(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun write(file: File, alias: String, value: String) {
        val text = encrypt(value, key(alias))
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp"); tmp.writeText(text)
        check(tmp.renameTo(file)) { "Could not save sync state" }
    }
    internal fun encrypt(value: String, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" + Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun gemini(context: Context): String = read(File(context.noBackupFilesDir, "gemini-subtitles.key"), "tgstream.gemini.subtitles")
    fun gemini(context: Context, value: String) {
        val file = File(context.noBackupFilesDir, "gemini-subtitles.key")
        if (value.isEmpty()) file.delete() else write(file, "tgstream.gemini.subtitles", value)
    }
}
