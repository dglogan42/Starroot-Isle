package com.starrootisle.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Continuous procedural cozy music loop (no asset files).
 * Soft pad + sparse pentatonic melody; night darkens the filter.
 */
class MusicEngine {
    @Volatile
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
            else if (wantPlay) start()
        }

    @Volatile
    var timeOfDay: Float = 0.3f

    @Volatile
    private var wantPlay = false

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun start() {
        wantPlay = true
        if (!enabled || running) return
        running = true
        val sampleRate = 22050
        val bufSamples = sampleRate / 4 // 250ms chunks
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, bufSamples * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        at.play()

        thread = Thread({
            val buf = ShortArray(bufSamples)
            var phasePad1 = 0.0
            var phasePad2 = 0.0
            var phaseBass = 0.0
            var phaseLead = 0.0
            var sampleIndex = 0L
            // C major pentatonic-ish (Hz)
            val scale = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25)
            val chords = arrayOf(
                doubleArrayOf(261.63, 329.63, 392.00), // C
                doubleArrayOf(220.00, 293.66, 349.23), // Am
                doubleArrayOf(246.94, 293.66, 369.99), // G/B-ish
                doubleArrayOf(220.00, 277.18, 329.63), // F-ish
            )
            var chord = 0
            var nextChordAt = 0L
            var nextNoteAt = 0L
            var leadFreq = 0.0
            var leadAmp = 0.0

            while (running && enabled) {
                val night = nightFactor(timeOfDay)
                val master = 0.11 * (1.0 - night * 0.25)
                val padBright = 1.0 - night * 0.45

                if (sampleIndex >= nextChordAt) {
                    chord = (chord + 1) % chords.size
                    nextChordAt = sampleIndex + sampleRate * 4L // 4s per chord
                }
                val ch = chords[chord]

                for (i in buf.indices) {
                    if (sampleIndex >= nextNoteAt) {
                        // sparse melody
                        if ((sampleIndex / 1000) % 5 != 0L) {
                            leadFreq = scale[(sampleIndex % scale.size).toInt()]
                            // step within scale
                            leadFreq = scale[((sampleIndex / 8000) + chord * 2).toInt() % scale.size]
                            leadAmp = 0.22 * padBright
                        } else {
                            leadAmp = 0.0
                        }
                        nextNoteAt = sampleIndex + (sampleRate * 0.55).toLong() + (sampleIndex % 3) * 2000
                    }

                    val t = sampleIndex.toDouble() / sampleRate
                    // slow LFO
                    val lfo = 0.5 + 0.5 * sin(2 * PI * 0.08 * t)

                    // pad
                    val f1 = ch[0] * 0.5
                    val f2 = ch[1]
                    val f3 = ch[2]
                    phasePad1 += 2 * PI * f1 / sampleRate
                    phasePad2 += 2 * PI * f2 / sampleRate
                    phaseBass += 2 * PI * (ch[0] * 0.25) / sampleRate
                    if (phasePad1 > 2 * PI) phasePad1 -= 2 * PI
                    if (phasePad2 > 2 * PI) phasePad2 -= 2 * PI
                    if (phaseBass > 2 * PI) phaseBass -= 2 * PI

                    val pad = (
                        soft(phasePad1) * 0.35 +
                            soft(phasePad2) * 0.28 * padBright +
                            soft(phasePad1 * (f3 / f1)) * 0.18 * padBright +
                            soft(phaseBass) * 0.22
                        ) * (0.85 + 0.15 * lfo)

                    // lead with simple envelope decay
                    phaseLead += 2 * PI * leadFreq / sampleRate
                    if (phaseLead > 2 * PI) phaseLead -= 2 * PI
                    leadAmp *= 0.9997
                    val lead = soft(phaseLead) * leadAmp

                    // gentle noise bed at night
                    val noise = if (night > 0.4) {
                        ((sampleIndex * 1103515245 + 12345) and 0x7fff) / 32768.0 - 0.5
                    } else 0.0

                    val sample = (pad * 0.55 + lead * 0.35 + noise * 0.02 * night) * master
                    buf[i] = (sample.coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
                    sampleIndex++
                }

                try {
                    at.write(buf, 0, buf.size)
                } catch (_: Exception) {
                    break
                }
            }
            try {
                at.stop()
                at.release()
            } catch (_: Exception) {
            }
            if (track === at) track = null
            running = false
        }, "starroot-music").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        wantPlay = false
        running = false
        try {
            thread?.join(400)
        } catch (_: Exception) {
        }
        thread = null
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    fun release() = stop()

    private fun soft(phase: Double): Double {
        // soft square-ish via sine + 3rd harmonic light
        return sin(phase) * 0.85 + sin(phase * 3) * 0.1
    }

    private fun nightFactor(tod: Float): Double {
        // 0 day, 1 night
        return when {
            tod < 0.2f || tod > 0.85f -> 1.0
            tod < 0.35f -> 1.0 - (tod - 0.2f) / 0.15f
            tod < 0.7f -> 0.0
            else -> ((tod - 0.7f) / 0.15f).toDouble()
        }.coerceIn(0.0, 1.0)
    }
}
