package com.tmplayer.data

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import com.tmplayer.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Explicit, app-only recording. No background upload or typed-text capture. */
object DiagnosticSession {
    data class Status(val serial: Long = 0, val recording: Boolean = false, val error: String? = null)
    private val mutable = MutableStateFlow(Status())
    val status = mutable.asStateFlow()
    private var app: Application? = null
    private var currentActivity = java.lang.ref.WeakReference<Activity>(null)
    @Volatile private var journal: DiagnosticJournal? = null
    @Volatile private var capturePaused = false
    private var logcat: java.lang.Process? = null
    private var reader: Thread? = null
    private var timer: java.util.Timer? = null
    private val dropped = AtomicLong()
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8192),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "TG-diagnostic-writer").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
    fun install(application: Application) {
        if (app === application) return
        app = application
        val prefs = application.getSharedPreferences("diagnostic-session", 0)
        mutable.value = Status(runCatching { prefs.getLong("serial", 0) }.getOrDefault(0).coerceAtLeast(0))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { journal?.record("CRASH", "thread=${thread.name}\n${error.stackTraceToString()}"); journal?.flush() }
            previous?.uncaughtException(thread, error)
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { event("ACTIVITY", "created ${a.javaClass.simpleName} restored=${b != null}") }
            override fun onActivityStarted(a: Activity) { event("ACTIVITY", "started ${a.javaClass.simpleName}") }
            override fun onActivityResumed(a: Activity) {
                currentActivity = java.lang.ref.WeakReference(a)
                event("ACTIVITY", "resumed ${a.javaClass.simpleName}")
                val original = a.window.callback ?: return
                if (original !is RecordingCallback) a.window.callback = RecordingCallback(original, a.javaClass.simpleName)
            }
            override fun onActivityPaused(a: Activity) { event("ACTIVITY", "paused ${a.javaClass.simpleName}") }
            override fun onActivityStopped(a: Activity) { event("ACTIVITY", "stopped ${a.javaClass.simpleName}") }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) { event("ACTIVITY", "saveState ${a.javaClass.simpleName}") }
            override fun onActivityDestroyed(a: Activity) { event("ACTIVITY", "destroyed ${a.javaClass.simpleName} changingConfiguration=${a.isChangingConfigurations}") }
        })
    }
    private class RecordingCallback(private val original: Window.Callback, private val screen: String) : Window.Callback by original {
        override fun dispatchTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP) event("INPUT", "$screen touch action=up")
            return original.dispatchTouchEvent(e)
        }
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.action == KeyEvent.ACTION_UP && e.keyCode in navigationKeys) event("INPUT", "$screen key=${e.keyCode}")
            return original.dispatchKeyEvent(e)
        }
    }
    private val navigationKeys = setOf(4, 19, 20, 21, 22, 23, 66, 82, 85, 86, 87, 88, 89, 90)
    fun event(source: String, message: String) {
        if (capturePaused) return
        val target = journal ?: return
        try { worker.execute { write(target, source, message) } }
        catch (_: java.util.concurrent.RejectedExecutionException) { dropped.incrementAndGet() }
    }
    private fun write(target: DiagnosticJournal, source: String, message: String) {
        if (capturePaused) return
        runCatching {
            val lost = dropped.getAndSet(0)
            if (lost > 0) target.record("CAPTURE_GAP", "writer queue full; dropped=$lost")
            target.record(source, message)
        }.onFailure {
            capturePaused = true
            mutable.value = mutable.value.copy(recording = false, error = it.message)
            timer?.cancel(); logcat?.destroy()
        }
    }
    @Synchronized fun start(): Long {
        journal?.let { return it.serial }
        val context = app ?: throw java.io.IOException("Diagnostic collector not initialized")
        val prefs = context.getSharedPreferences("diagnostic-session", 0)
        val interrupted = runCatching { prefs.getBoolean("active", false) }.getOrDefault(false)
        val serial = runCatching { prefs.getLong("serial", 0) }.getOrDefault(0).coerceIn(0, Long.MAX_VALUE - 1) + 1
        val root = File(context.filesDir, "diagnostics/session-${serial.toString().padStart(6, '0')}")
        val target = DiagnosticJournal(root, serial)
        try {
            target.record("SESSION_START", "serial=$serial version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} package=${context.packageName} android=${Build.VERSION.SDK_INT} model=${Build.MODEL} abis=${Build.SUPPORTED_ABIS.joinToString()} pid=${Process.myPid()} maxHeap=${Runtime.getRuntime().maxMemory()}")
            target.flush()
            if (!prefs.edit().putLong("serial", serial).putBoolean("active", true).commit()) throw java.io.IOException("Cannot persist log serial")
        } catch (e: Exception) { runCatching { target.close() }; throw e }
        capturePaused = false; dropped.set(0); journal = target; mutable.value = Status(serial, true)
        if (interrupted) event("PREVIOUS_SESSION", "Previous process ended before Stop; files retained under diagnostics")
        event("COVERAGE", "app logcat, lifecycle, navigation, touch actions, exceptions, playback, periodic device and network state; credentials and typed text excluded; capture gaps explicitly reported")
        runCatching {
            val snapshot = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(3000) { SettingsStore(context).rawEntries() } }
            val strings = setOf("theme_choice", "ui_scale", "language", "subtitle_size", "subtitle_position", "subtitle_mode", "preferred_audio_language", "preferred_subtitle_language")
            for ((key, value) in snapshot.toSortedMap()) {
                if (!Regex("(?i)pin|auth|token|secret|api|password|phone").containsMatchIn(key) && (value is Boolean || value is Number || key in strings)) event("SETTING", "$key=$value")
            }
        }.onFailure { event("CAPTURE_ERROR", "settings snapshot: ${it.javaClass.simpleName}") }
        runCatching {
            val args = mutableListOf("logcat", "-v", "threadtime", "-T", "1")
            if (Build.VERSION.SDK_INT >= 24) args += "--pid=${Process.myPid()}"
            val process = ProcessBuilder(args).redirectErrorStream(true).start(); logcat = process
            reader = Thread({
                runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (journal === target) write(target, "LOGCAT", it) } } }
                    .onFailure { if (journal === target) write(target, "CAPTURE_ERROR", "logcat: ${it.stackTraceToString()}") }
            }, "TG-logcat").apply { isDaemon = true; start() }
        }.onFailure { write(target, "CAPTURE_ERROR", "logcat unavailable: ${it.stackTraceToString()}") }
        timer = java.util.Timer("TG-diagnostic-sampler", true).apply { scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                runCatching {
                    val runtime = Runtime.getRuntime()
                    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val network = cm?.activeNetworkInfo
                    event("SAMPLE", "elapsedMs=${SystemClock.elapsedRealtime()} heapUsed=${runtime.totalMemory()-runtime.freeMemory()} heapMax=${runtime.maxMemory()} nativeHeap=${android.os.Debug.getNativeHeapAllocatedSize()} freeBytes=${context.filesDir.usableSpace} network=${network?.typeName} connected=${network?.isConnected} threads=${Thread.activeCount()}")
                    android.os.Handler(context.mainLooper).post {
                        (currentActivity.get() as? com.tmplayer.player.PlayerActivity)?.let { a -> runCatching { a.diagnosticSnapshot() }.onSuccess { event("PLAYBACK", it) }.onFailure { event("CAPTURE_ERROR", "player snapshot ${it.javaClass.simpleName}") } }
                    }
                    target.flush()
                }.onFailure { event("CAPTURE_ERROR", "sampler ${it.javaClass.simpleName}") }
            }
        }, 0, 1000) }
        return serial
    }
    @Synchronized fun stop(): File {
        val context = app ?: throw java.io.IOException("Diagnostic collector not initialized")
        val target = journal
        if (target == null) {
            val saved = export(context, mutable.value.serial)
            context.getSharedPreferences("diagnostic-session", 0).edit().putBoolean("active", false).apply()
            return saved
        }
        timer?.cancel(); timer = null; logcat?.destroy(); logcat = null
        reader?.join(1500); reader = null
        val barrier = java.util.concurrent.FutureTask<Unit>({ Unit })
        try { worker.execute(barrier) }
        catch (_: java.util.concurrent.RejectedExecutionException) { worker.queue.put(barrier) }
        barrier.get(10, TimeUnit.SECONDS)
        runCatching { target.record("SESSION_STOP", "serial=${target.serial} dropped=${dropped.get()} captureError=${mutable.value.error.orEmpty()}") }
        journal = null
        runCatching { target.close() }.onFailure { mutable.value = mutable.value.copy(error = it.message) }
        context.getSharedPreferences("diagnostic-session", 0).edit().putBoolean("active", false).apply()
        mutable.value = mutable.value.copy(recording = false)
        return export(context, target.serial)
    }
    private fun export(context: Application, serial: Long): File {
        val folder = File(context.filesDir, "diagnostics/session-${serial.toString().padStart(6, '0')}")
        val parts = folder.listFiles()?.filter { it.name.startsWith("part-") && it.extension == "txt" }?.sortedBy { it.name }.orEmpty()
        if (parts.isEmpty()) throw java.io.IOException("No recorded session")
        val compressed = parts.sumOf { it.length() } > 8L * 1024 * 1024
        val output = File(context.cacheDir, "logs/TG-log-${serial.toString().padStart(6, '0')}.${if (compressed) "zip" else "txt"}")
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, output.name + ".partial")
        try {
            if (compressed) java.util.zip.ZipOutputStream(temporary.outputStream().buffered()).use { out ->
                for (part in parts) { out.putNextEntry(java.util.zip.ZipEntry(part.name)); part.inputStream().use { it.copyTo(out) }; out.closeEntry() }
                out.putNextEntry(java.util.zip.ZipEntry("session.txt")); out.write("TG Stream session $serial; parts are in numeric sequence order.\n".toByteArray()); out.closeEntry()
            } else temporary.outputStream().buffered().use { out -> for (part in parts) part.inputStream().use { it.copyTo(out) } }
            if (!temporary.renameTo(output)) throw java.io.IOException("Cannot finalize diagnostic export")
        } catch (e: Exception) { temporary.delete(); throw e }
        return output
    }
}
