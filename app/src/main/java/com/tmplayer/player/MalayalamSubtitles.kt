package com.tmplayer.player

import android.app.AlertDialog
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.tmplayer.data.guardedLifecycleScope as lifecycleScope
import com.tmplayer.i18n.L
import com.tmplayer.online.SubtitleText
import com.tmplayer.online.SubtitleTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

/** User-selected subtitle file only. Originals and timing lines stay untouched. */
class MalayalamSubtitles(private val activity: FragmentActivity, private val subtitles: SubtitleFiles,
    private val media: () -> String, private val say: (String) -> Unit) {
    private var work: Job? = null
    private var progress: AlertDialog? = null
    private val secrets by lazy { File(activity.noBackupFilesDir, "gemini-subtitles.key") }
    private fun text(key: String) = L.messages.text("player.$key")
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) translate(uri)
    }
    fun open() {
        if (work?.isActive == true) { say(text("translation_busy")); return }
        val key = EditText(activity).apply {
            hint = text("translation_key"); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(runCatching { readKey() }.getOrDefault(""))
        }
        val prefs = activity.getSharedPreferences("subtitle_translation", 0)
        val model = EditText(activity).apply { hint = text("translation_model"); setText(prefs.getString("model", "gemini-3.8-flash")); setSingleLine() }
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8); addView(key); addView(model) }
        AlertDialog.Builder(activity).setTitle(L.playerTranslateMalayalam).setMessage(text("translation_consent"))
            .setView(layout).setNegativeButton(L.commonCancel, null)
            .setNeutralButton(text("translation_forget")) { _, _ -> secrets.delete() }
            .setPositiveButton(text("translation_choose")) { _, _ ->
                val value = key.text.toString().trim(); val selectedModel = model.text.toString().trim()
                if (value.isBlank() || !selectedModel.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { say(text("translation_key")); return@setPositiveButton }
                if (runCatching { saveKey(value) }.isFailure) { say(text("translation_key_failed")); return@setPositiveButton }
                prefs.edit().putString("model", selectedModel).apply()
                val files = subtitles.files.filter { it.label?.endsWith(".ml.srt") != true }
                val labels = files.map { it.label ?: text("translation_file") } + text("translation_choose")
                AlertDialog.Builder(activity).setTitle(text("translation_source")).setItems(labels.toTypedArray()) { _, which ->
                    if (which < files.size) translate(files[which].uri, files[which].label)
                    else runCatching { picker.launch(arrayOf("*/*")) }.onFailure { say(L.tracksNoFilePicker) }
                }.setNegativeButton(L.commonCancel, null).show()
            }.show()
    }
    private fun translate(uri: Uri, label: String? = null) {
        val anchor = media()
        work = activity.lifecycleScope.launch {
            var temporary: File? = null
            progress = AlertDialog.Builder(activity).setTitle(L.playerTranslateMalayalam).setMessage(text("translation_busy"))
                .setNegativeButton(L.commonCancel) { _, _ -> work?.cancel() }.setOnCancelListener { work?.cancel() }.show()
            try {
                val file = withContext(Dispatchers.IO) {
                    val name = label ?: activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val extension = name.substringAfterLast('.', "").lowercase()
                    val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= SubtitleText.MAX_BYTES) { "translation_invalid" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    } ?: throw IllegalStateException("translation_invalid")
                    val decoded = SubtitleText.decode(bytes)
                    val doc = SubtitleTranslation.parse(decoded, extension) ?: throw IllegalStateException("translation_invalid")
                    val translated = mutableListOf<String>()
                    val key = readKey(); val model = activity.getSharedPreferences("subtitle_translation", 0).getString("model", "gemini-3.8-flash") ?: "gemini-3.8-flash"
                    for (batch in doc.dialogue.chunked(30)) {
                        ensureActive()
                        translated += SubtitleTranslation.batch(key, model, batch.map { it.text })
                    }
                    ensureActive()
                    File.createTempFile("translated-", ".ml.$extension", activity.cacheDir).also {
                        temporary = it
                        it.writeText(doc.translated(translated), Charsets.UTF_8)
                    }
                }
                ensureActive()
                if (media() == anchor) subtitles.attachChatFile(file, "Malayalam.ml.${file.extension}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val status = e.message?.takeIf { it.matches(Regex("Gemini HTTP [0-9]{3}")) }.orEmpty()
                say(text("translation_failed") + if (status.isBlank()) "" else " ($status)")
            } finally {
                progress?.dismiss(); progress = null
                // SubtitleFiles copies asynchronously into its managed cache before use.
                temporary?.let { file -> activity.window.decorView.postDelayed({ file.delete() }, 60_000) }
            }
        }
    }
    fun close() { work?.cancel(); progress?.dismiss(); progress = null }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tgstream.gemini.subtitles", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tgstream.gemini.subtitles", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun saveKey(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        secrets.writeText(Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" + Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }
    private fun readKey(): String {
        if (!secrets.exists() || secrets.length() > 16_384) return ""
        val parts = secrets.readLines(); check(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
