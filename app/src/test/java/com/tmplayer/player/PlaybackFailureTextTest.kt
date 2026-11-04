package com.tmplayer.player

import androidx.media3.common.PlaybackException
import com.tmplayer.i18n.L
import org.junit.Assert.*
import org.junit.Test

class PlaybackFailureTextTest {
    @Test fun `decoder missing file timeout and unknown codes stay distinguishable`() {
        assertEquals(L.playerErrorDecoder, PlaybackFailureText.fallback(PlaybackException.ERROR_CODE_DECODING_FAILED, "DECODER"))
        assertEquals(L.errorsFileMoved, PlaybackFailureText.fallback(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, "FILE"))
        assertEquals(L.playerErrorTimedOut, PlaybackFailureText.fallback(PlaybackException.ERROR_CODE_TIMEOUT, "TIMEOUT"))
        assertTrue(PlaybackFailureText.fallback(99999, "UNKNOWN_99999").contains("UNKNOWN_99999"))
    }
}
