package com.tmplayer.player

import androidx.media3.common.PlaybackException
import com.tmplayer.i18n.L

/** Names only causes known from the player code; unknown codes are kept visible, not guessed. */
object PlaybackFailureText {
    fun fallback(code: Int, name: String): String = when (code) {
        PlaybackException.ERROR_CODE_DECODING_FAILED -> L.playerErrorDecoder
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> L.errorsFileMoved
        PlaybackException.ERROR_CODE_TIMEOUT -> L.playerErrorTimedOut
        else -> L.playerErrorWithCode(name)
    }
}
