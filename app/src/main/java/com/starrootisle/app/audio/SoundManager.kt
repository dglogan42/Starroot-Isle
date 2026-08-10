package com.starrootisle.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight synthesized SFX — no external audio assets.
 * Soft pentatonic-ish blips for a cozy feel.
 */
class SoundManager {
    @Volatile
    var enabled: Boolean = true

    private val thread = HandlerThread("starroot-sfx").apply { start() }
    private val handler = Handler(thread.looper)

    enum class Sfx {
        UI, STEP, HOE, WATER, PLANT, MINE, CHOP, HARVEST,
        FISH, CRAFT, SLEEP, BOND, LEVEL, ERROR, AMBIENT
    }

    fun play(sfx: Sfx) {
        if (!enabled) return
        handler.post {
            try {
                when (sfx) {
                    Sfx.UI -> blip(880.0, 40, 0.18)
                    Sfx.STEP -> blip(180.0, 25, 0.06)
                    Sfx.HOE -> chirp(220.0, 160.0, 70, 0.2)
                    Sfx.WATER -> chirp(600.0, 900.0, 90, 0.12)
                    Sfx.PLANT -> blip(523.25, 60, 0.15)
                    Sfx.MINE -> noiseBurst(50, 0.15)
                    Sfx.CHOP -> chirp(300.0, 120.0, 60, 0.18)
                    Sfx.HARVEST -> arpeggio(listOf(523.25, 659.25, 783.99), 45, 0.14)
                    Sfx.FISH -> chirp(400.0, 700.0, 120, 0.12)
                    Sfx.CRAFT -> arpeggio(listOf(392.0, 493.88, 587.33), 50, 0.16)
                    Sfx.SLEEP -> chirp(440.0, 220.0, 200, 0.12)
                    Sfx.BOND -> arpeggio(listOf(523.25, 659.25, 783.99, 1046.5), 55, 0.14)
                    Sfx.LEVEL -> arpeggio(listOf(392.0, 523.25, 659.25, 783.99), 70, 0.18)
                    Sfx.ERROR -> blip(140.0, 90, 0.15)
                    Sfx.AMBIENT -> softPad(120)
                }
            } catch (_: Exception) {
                // Ignore AudioTrack race on rapid taps
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    private fun blip(freq: Double, ms: Int, volume: Double) {
        tone(ms) { t, _ -> sin(2 * PI * freq * t) * volume * envelope(t, ms / 1000.0) }
    }

    private fun chirp(f0: Double, f1: Double, ms: Int, volume: Double) {
        val dur = ms / 1000.0
        tone(ms) { t, _ ->
            val f = f0 + (f1 - f0) * (t / dur)
            sin(2 * PI * f * t) * volume * envelope(t, dur)
        }
    }

    private fun arpeggio(freqs: List<Double>, noteMs: Int, volume: Double) {
        val total = noteMs * freqs.size
        val noteDur = noteMs / 1000.0
        tone(total) { t, _ ->
            val idx = (t / noteDur).toInt().coerceIn(0, freqs.lastIndex)
            val local = t - idx * noteDur
            sin(2 * PI * freqs[idx] * local) * volume * envelope(local, noteDur)
        }
    }

    private fun noiseBurst(ms: Int, volume: Double) {
        var state = 0xACE1
        tone(ms) { t, _ ->
            state = (state xor (state shl 13))
            state = (state xor (state ushr 17))
            state = (state xor (state shl 5))
            val n = ((state and 0xFFFF) / 32768.0 - 1.0)
            n * volume * envelope(t, ms / 1000.0)
        }
    }

    private fun softPad(ms: Int) {
        tone(ms) { t, _ ->
            val a = sin(2 * PI * 220.0 * t) * 0.04
            val b = sin(2 * PI * 277.18 * t) * 0.03
            (a + b) * envelope(t, ms / 1000.0)
        }
    }

    private fun envelope(t: Double, dur: Double): Double {
        val attack = 0.01
        val release = 0.04
        return when {
            t < attack -> t / attack
            t > dur - release -> ((dur - t) / release).coerceAtLeast(0.0)
            else -> 1.0
        }
    }

    private fun tone(ms: Int, sample: (t: Double, i: Int) -> Double) {
        val sampleRate = 22050
        val n = (sampleRate * ms / 1000).coerceAtLeast(1)
        val buf = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val v = sample(t, i).coerceIn(-1.0, 1.0)
            buf[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.setNotificationMarkerPosition(buf.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                t?.release()
            }

            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        track.play()
        // Fallback release
        handler.postDelayed({
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }, (ms + 80).toLong())
    }
}
