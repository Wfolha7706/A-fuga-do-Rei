package com.jungleclimb.afugadorei

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class SoundEngine(private val context: Context, private val saveManager: SaveManager) {

    private val sfxExecutor = Executors.newCachedThreadPool()
    @Volatile private var musicRunning = false
    @Volatile private var currentWorldTheme = 0 // 0 = Menu, 1..4 = Mundos
    private var musicThread: Thread? = null

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    fun vibrate(durationMs: Long) {
        if (!saveManager.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun startMusic(worldTheme: Int) {
        currentWorldTheme = worldTheme
        if (!saveManager.musicEnabled) {
            stopMusic()
            return
        }
        if (musicRunning) return
        musicRunning = true

        musicThread = Thread {
            var track: AudioTrack? = null
            try {
                val sampleRate = 44100
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = if (minBuf > 0) minBuf.coerceAtLeast(4096) else 4096

                track = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val melodies = mapOf(
                    0 to intArrayOf(262, 330, 392, 523, 392, 330, 294, 349, 440, 349), // Menu
                    1 to intArrayOf(220, 262, 294, 330, 294, 262, 196, 247),           // Mundo 1: Canteiro
                    2 to intArrayOf(294, 349, 440, 392, 349, 330, 262, 294),           // Mundo 2: Selva
                    3 to intArrayOf(196, 233, 262, 277, 262, 233, 196, 175),           // Mundo 3: Fábrica
                    4 to intArrayOf(165, 196, 220, 233, 220, 196, 165, 147)            // Mundo 4: Fortaleza
                )

                track.play()
                var step = 0
                val noteDurationSamples = sampleRate / 6
                val buffer = ShortArray(noteDurationSamples)

                while (musicRunning && saveManager.musicEnabled) {
                    val notes = melodies[currentWorldTheme] ?: melodies[1]!!
                    val freq = notes[step % notes.size].toDouble()
                    for (i in 0 until noteDurationSamples) {
                        val env = if (i < noteDurationSamples * 0.8) 1.0 else 0.0
                        val wave = sin(2.0 * PI * freq * i / sampleRate)
                        buffer[i] = (wave * 1800 * env).toInt().toShort()
                    }
                    track.write(buffer, 0, buffer.size)
                    step++
                }
            } catch (_: Exception) {
            } finally {
                musicRunning = false
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }.apply { isDaemon = true; start() }
    }

    fun stopMusic() {
        musicRunning = false
        musicThread = null
    }

    private fun playToneSequence(freqs: IntArray, durationsMs: IntArray, volume: Int = 4500) {
        if (!saveManager.sfxEnabled) return
        sfxExecutor.execute {
            var track: AudioTrack? = null
            try {
                val sampleRate = 44100
                val totalMs = durationsMs.sum().coerceAtLeast(20)
                val totalSamples = (sampleRate * totalMs) / 1000
                val pcm = ShortArray(totalSamples)
                var cursor = 0

                for (n in freqs.indices) {
                    val f = freqs[n].toDouble()
                    val samples = (sampleRate * durationsMs[n]) / 1000
                    for (i in 0 until samples) {
                        if (cursor >= totalSamples) break
                        val fade = 1.0 - (i.toDouble() / samples) * 0.5
                        val s = if (f > 0) sin(2.0 * PI * f * i / sampleRate) else 0.0
                        pcm[cursor++] = (s * volume * fade).toInt().toShort()
                    }
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufBytes = (pcm.size * 2).coerceAtLeast(if (minBuf > 0) minBuf else 2048)

                track = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(totalMs.toLong() + 40L)
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun playJump() = playToneSequence(intArrayOf(320, 480, 640), intArrayOf(35, 35, 50))
    fun playStep() = playToneSequence(intArrayOf(180), intArrayOf(25), 2000)
    fun playClimb() = playToneSequence(intArrayOf(260, 310), intArrayOf(25, 25), 2500)
    fun playCoin() = playToneSequence(intArrayOf(587, 880), intArrayOf(50, 90))
    fun playBarrelBounce() = playToneSequence(intArrayOf(140, 110), intArrayOf(40, 40), 3200)
    fun playDamage() {
        vibrate(120)
        playToneSequence(intArrayOf(300, 220, 150), intArrayOf(70, 70, 120), 6000)
    }
    fun playBossRoar() {
        vibrate(80)
        playToneSequence(intArrayOf(130, 110, 95, 120), intArrayOf(80, 80, 80, 120), 6500)
    }
    fun playVictory() = playToneSequence(intArrayOf(523, 659, 784, 1046), intArrayOf(90, 90, 90, 220), 5500)
    fun playGameOver() = playToneSequence(intArrayOf(392, 349, 330, 262), intArrayOf(120, 120, 120, 260), 5500)
}
