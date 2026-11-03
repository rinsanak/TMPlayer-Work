package com.tmplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.tmplayer.data.DiagnosticSession
import com.tmplayer.i18n.L
import com.tmplayer.ui.components.TmButton
import com.tmplayer.ui.components.TmSecondaryButton
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.glassPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiagnosticControls(modifier: Modifier = Modifier) {
    val state by DiagnosticSession.status.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val say = rememberToast()
    var busy by remember { mutableStateOf(false) }
    Column(modifier.background(Tone.surface, RoundedCornerShape(24.dp)).glassPanel().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.serial > 0) Text(L.logsSessionNumber(state.serial), color = Tone.text)
        if (state.error != null) Text(L.logsFailed, color = Tone.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TmButton(enabled = !busy && !state.recording && state.error == null, onClick = {
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { DiagnosticSession.start() } }.onFailure { say(L.logsFailed) }
                    busy = false
                }
            }) { Text(L.logsSessionStart) }
            TmSecondaryButton(enabled = !busy && state.serial > 0, onClick = {
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { DiagnosticSession.stop() } }.onSuccess { file ->
                        val place = withContext(Dispatchers.IO) { runCatching { SharedDownloads.copyDiagnostic(context, file) }.getOrNull() }
                        if (place != null) say(L.logsSavedTo(place))
                        if (!shareDiagnosticFile(context, file) && place == null) say(L.logsFailed)
                    }.onFailure { say(L.logsFailed) }
                    busy = false
                }
            }) { Text(L.logsSessionStop) }
        }
    }
}
