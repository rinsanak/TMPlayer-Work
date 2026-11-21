package com.tmplayer.ui.settings

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tmplayer.data.DiagnosticSession
import com.tmplayer.data.DiagnosticUpload
import com.tmplayer.i18n.L
import com.tmplayer.platform.Background
import com.tmplayer.ui.components.TmButton
import com.tmplayer.ui.components.TmSecondaryButton
import com.tmplayer.ui.components.showMessage
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.glassPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiagnosticControls(modifier: Modifier = Modifier) {
    val state by DiagnosticSession.status.collectAsState()
    val busy by DiagnosticActions.busy.collectAsState()
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(DiagnosticSession.autoStartEnabled()) }
    Column(modifier.background(Tone.surface, RoundedCornerShape(24.dp)).glassPanel().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(
            title = L.logsAutoStart,
            subtitle = L.logsAutoStart,
            icon = com.tmplayer.ui.icons.TmIcons.Logs,
            checked = autoStart,
            onToggle = {
                autoStart = !autoStart
                DiagnosticSession.setAutoStartEnabled(autoStart)
            },
        )
        if (state.serial > 0) Text(L.logsSessionNumber(state.serial), color = Tone.text)
        if (state.error != null) Text(L.logsFailed, color = Tone.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TmButton(enabled = !busy && !state.pendingExport, onClick = {
                DiagnosticActions.run(context, stop = false)
            }) { Text(L.logsSessionStart) }
            TmSecondaryButton(enabled = !busy && state.serial > 0, onClick = {
                DiagnosticActions.run(context, stop = true)
            }) { Text(L.logsSessionStop) }
        }
    }
}

/** The remote sees one focus target throughout recording and export. */
@Composable
internal fun TvDiagnosticToggle(modifier: Modifier = Modifier) {
    val state by DiagnosticSession.status.collectAsState()
    val busy by DiagnosticActions.busy.collectAsState()
    val context = LocalContext.current
    DiagnosticToggleButton(
        stop = state.pendingExport,
        busy = busy,
        onClick = { DiagnosticActions.run(context, stop = DiagnosticSession.status.value.pendingExport) },
        modifier = modifier,
    )
}

@Composable
internal fun DiagnosticToggleButton(
    stop: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (stop) L.logsSessionStop else L.logsSessionStart
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val ink = when {
        focused -> Tone.onFocusFill
        stop -> Tone.danger
        else -> Tone.text
    }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (focused) Modifier.background(Tone.focusFill) else Modifier)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = { if (!busy) onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val color = if (busy) ink.copy(alpha = 0.5f) else ink
            if (stop) drawRect(color) else drawCircle(color)
        }
    }
}

private object DiagnosticActions {
    val busy = MutableStateFlow(false)

    fun run(context: Context, stop: Boolean) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        val app = context.applicationContext
        Background.scope.launch(Dispatchers.Main.immediate) {
            try {
                if (stop) {
                    val file = withContext(Dispatchers.IO) { DiagnosticSession.stop() }
                    try {
                        DiagnosticUpload.send(file)
                        showMessage(app, L.logsSavedTo(L.chatsSavedMessages))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        showMessage(app, L.logsFailed)
                        val place = withContext(Dispatchers.IO) {
                            runCatching { SharedDownloads.copyDiagnostic(app, file) }.getOrNull()
                        }
                        if (place != null) showMessage(app, L.logsSavedTo(place))
                        shareDiagnosticFile(app, file)
                    }
                } else {
                    withContext(Dispatchers.IO) { DiagnosticSession.start() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showMessage(app, L.logsFailed)
            } finally {
                busy.value = false
            }
        }
    }
}
