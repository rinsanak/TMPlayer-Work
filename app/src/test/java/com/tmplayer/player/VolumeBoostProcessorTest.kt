package com.tmplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class VolumeBoostProcessorTest {

    @Test fun `dialogue mode lifts quiet speech band more than bass without clipping stereo PCM`() {
        val stereo = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        fun tone(hz: Int, amplitude: Float, dialogue: Boolean): List<Float> {
            val processor = VolumeBoostProcessor().apply { configure(stereo); flush(); level = false; this.dialogue = dialogue }
            val input = ByteBuffer.allocateDirect(48_000 * 4).order(ByteOrder.nativeOrder())
            repeat(48_000) { n -> val v = (kotlin.math.sin(2 * Math.PI * hz * n / 48_000) * amplitude * 32767).toInt().toShort(); input.putShort(v); input.putShort(v) }
            input.flip()
            return run(processor, input)
        }
        fun rms(samples: List<Float>): Double = kotlin.math.sqrt(samples.takeLast(24_000).map { it.toDouble() * it }.average())
        val original = tone(1500, 0.04f, false)
        val speech = tone(1500, 0.04f, true)
        val bass = tone(60, 0.04f, true)
        assertTrue(rms(speech) > rms(original) * 2)
        assertTrue(rms(speech) > rms(bass) * 1.2)
        val loud = tone(1500, 0.99f, true)
        assertTrue(loud.all { it.isFinite() && abs(it) <= 1f })
    }

    @Test fun `dialogue float path limits transients and OFF returns to unprocessed stereo`() {
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        val p = VolumeBoostProcessor().apply { configure(format); flush(); level = false; dialogue = true }
        fun samples(value: Float, frames: Int) = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putFloat(value); putFloat(-value) }; flip()
        }
        p.queueInput(samples(0.99f, 4800))
        val boosted = p.output
        while (boosted.hasRemaining()) assertTrue(abs(boosted.float) <= 1f)
        p.dialogue = false
        p.queueInput(samples(0.03f, 48_000))
        val settled = p.output
        var last = 0f
        while (settled.hasRemaining()) last = settled.float
        assertEquals(-0.03f, last, 0.00001f)
    }

    /** 48 kHz mono 16 bit. */
    private val format = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)

    private fun processor(on: Boolean, night: Boolean = false, level: Boolean = false) = VolumeBoostProcessor().apply {
        configure(format)
        flush()
        enabled = on
        this.night = night
        this.level = level
    }

    /** A steady square wave at [level] of full scale, which keeps the peak follower simple. */
    private fun square(level: Float, frames: Int) =
        ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putShort(((if (it % 2 == 0) level else -level) * 32767).toInt().toShort()) }
            flip()
        }

    private fun run(processor: VolumeBoostProcessor, input: ByteBuffer): List<Float> {
        processor.queueInput(input)
        val out = processor.output
        return buildList { while (out.hasRemaining()) add(out.short / 32768f) }
    }

    @Test
    fun `off passes the sound through untouched`() {
        val out = run(processor(false), square(0.5f, 4800))
        assertEquals(4800, out.size)
        assertEquals(0.5f, abs(out.last()), 0.001f)
    }

    @Test
    fun `quiet sound is lifted by the whole boost`() {
        val out = run(processor(true), square(0.05f, 48_000))
        assertEquals(0.05f * VolumeBoostProcessor.BOOST, abs(out.last()), 0.005f)
    }

    @Test
    fun `loud sound is squeezed well below full scale once the follower has caught it`() {
        val out = run(processor(true), square(1f, 48_000))
        val settled = abs(out.last())
        assertTrue("settled at $settled", settled in 0.5f..0.7f)
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test
    fun `switching on ramps the gain instead of jumping`() {
        val out = run(processor(true), square(0.05f, 48_000))
        assertTrue(abs(out.first()) < 0.06f)
    }

    @Test
    fun `night mode makes the loudest scenes quieter than they were`() {
        val out = run(processor(false, night = true), square(1f, 48_000))
        val settled = abs(out.last())
        // A full scale peak lands near -11 dBFS: well under where it started.
        assertTrue("settled at $settled", settled in 0.2f..0.35f)
        assertEquals(VolumeBoostProcessor.settledOutput(1f, boost = false, night = true), settled, 0.03f)
    }

    @Test
    fun `night mode lifts quiet dialogue`() {
        val out = run(processor(false, night = true), square(0.03f, 48_000))
        assertEquals(0.03f * VolumeBoostProcessor.NIGHT_GAIN, abs(out.last()), 0.004f)
    }

    @Test
    fun `night mode narrows the gap between a whisper and an explosion`() {
        val loud = VolumeBoostProcessor.settledOutput(1f, boost = false, night = true)
        val quiet = VolumeBoostProcessor.settledOutput(0.03f, boost = false, night = true)
        // From 30 dB apart to under 15.
        assertTrue(20 * kotlin.math.log10(loud / quiet) < 15f)
    }

    @Test
    fun `boost and night mode together never clip`() {
        val out = run(processor(true, night = true), square(1f, 48_000))
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test
    fun `even out volume lifts a quiet track towards its target without clipping`() {
        // Twenty seconds at about -30 dBFS: many of the leveller's time constants.
        val out = run(processor(false, level = true), square(0.03f, 960_000))
        val settled = abs(out.last())
        assertEquals(0.03f * VolumeBoostProcessor.levelFor(0.03f * 0.03f), settled, 0.006f)
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test
    fun `even out volume sounds the same however the sound is cut into buffers`() {
        // The leveller's aim is worked out once a block of frames, counted across buffers, so a
        // buffer size that does not divide the block changes nothing.
        val whole = run(processor(false, level = true), square(0.03f, 48_000))
        val cut = processor(false, level = true)
        val source = square(0.03f, 48_000)
        val pieces = ArrayList<Float>()
        while (source.hasRemaining()) {
            val size = minOf(source.remaining(), 2 * 997)
            val piece = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            repeat(size) { piece.put(source.get()) }
            piece.flip()
            pieces += run(cut, piece)
        }
        assertEquals(whole, pieces)
    }
}
