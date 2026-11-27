package com.threeDLedger.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight Studio-Synthesized Tactile Sound Manager
 * ────────────────────────────────────────────────────
 * Initializes off the main thread so cold start has 0ms UI jank,
 * generates compact 16-bit PCM acoustic waveforms once in cache,
 * and uses lock-free AtomicIntegerArray lookups for lag-free polyphonic playback.
 */
class SoundManager private constructor(context: Context) {

    enum class SfxType(val fileName: String) {
        KEY_TAP("sfx_key_tap_v2.wav"),
        KEY_SOFT("sfx_soft_tap_v2.wav"),
        KEY_DELETE("sfx_delete_tap_v2.wav"),
        COPY_CHIME("sfx_copy_chime_v2.wav"),
        SUCCESS_CHIME("sfx_success_chime_v2.wav"),
        ERROR_THUD("sfx_error_thud_v2.wav"),
        WINNER_FANFARE("sfx_winner_fanfare_v2.wav")
    }

    companion object {
        private const val TAG = "SoundManager"
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_SOUND_ENABLED = "soundEnabled"
        private const val KEY_SOUND_VOLUME_LEVEL = "soundVolumeLevel" // 0=Soft, 1=Normal, 2=Clear
        private const val SAMPLE_RATE = 16000 // Crisp voice-grade PCM: 27% smaller & faster load

        @Volatile
        private var instance: SoundManager? = null

        @JvmStatic
        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }

        fun buildWavForType(type: SfxType): ByteArray {
            val samples = when (type) {
                SfxType.KEY_TAP -> synthesizePercussiveClick(
                    durationMs = 18,
                    startFreq = 860.0,
                    endFreq = 290.0,
                    bodyFreq = 195.0,
                    amplitude = 0.65
                )
                SfxType.KEY_SOFT -> synthesizePluck(
                    durationMs = 36,
                    freqs = doubleArrayOf(540.0, 1080.0),
                    weights = doubleArrayOf(0.75, 0.25),
                    decayRate = 90.0,
                    amplitude = 0.55
                )
                SfxType.KEY_DELETE -> synthesizePercussiveClick(
                    durationMs = 26,
                    startFreq = 310.0,
                    endFreq = 135.0,
                    bodyFreq = 120.0,
                    amplitude = 0.62
                )
                SfxType.COPY_CHIME -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(659.25, 987.77),
                    noteSpacingMs = 36,
                    noteDurationMs = 58,
                    amplitude = 0.55
                )
                SfxType.SUCCESS_CHIME -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50),
                    noteSpacingMs = 42,
                    noteDurationMs = 95,
                    amplitude = 0.58
                )
                SfxType.ERROR_THUD -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(220.0, 164.81),
                    noteSpacingMs = 75,
                    noteDurationMs = 90,
                    amplitude = 0.68
                )
                SfxType.WINNER_FANFARE -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51),
                    noteSpacingMs = 48,
                    noteDurationMs = 125,
                    amplitude = 0.60
                )
            }
            return encodePcm16MonoWav(samples, SAMPLE_RATE)
        }

        private fun synthesizePercussiveClick(
            durationMs: Int,
            startFreq: Double,
            endFreq: Double,
            bodyFreq: Double,
            amplitude: Double
        ): ShortArray {
            val count = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
            val out = ShortArray(count)
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / count
                val instantFreq = startFreq + (endFreq - startFreq) * progress
                phase += 2.0 * PI * instantFreq / SAMPLE_RATE
                val env = exp(-progress * 8.5)
                val body = sin(2.0 * PI * bodyFreq * t) * 0.45
                val click = sin(phase) * 0.55
                val sample = ((click + body) * env * amplitude).coerceIn(-1.0, 1.0)
                out[i] = (sample * Short.MAX_VALUE).roundToInt().toShort()
            }
            return out
        }

        private fun synthesizePluck(
            durationMs: Int,
            freqs: DoubleArray,
            weights: DoubleArray,
            decayRate: Double,
            amplitude: Double
        ): ShortArray {
            val count = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
            val out = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val attack = (i.toDouble() / (SAMPLE_RATE * 0.002).coerceAtLeast(1.0)).coerceAtMost(1.0)
                val env = attack * exp(-decayRate * t)
                var sum = 0.0
                for (n in freqs.indices) {
                    sum += sin(2.0 * PI * freqs[n] * t) * weights[n]
                }
                val sample = (sum * env * amplitude).coerceIn(-1.0, 1.0)
                out[i] = (sample * Short.MAX_VALUE).roundToInt().toShort()
            }
            return out
        }

        private fun synthesizeArpeggio(
            notesHz: DoubleArray,
            noteSpacingMs: Int,
            noteDurationMs: Int,
            amplitude: Double
        ): ShortArray {
            val totalMs = (notesHz.size - 1) * noteSpacingMs + noteDurationMs
            val totalSamples = (SAMPLE_RATE * totalMs / 1000).coerceAtLeast(1)
            val mix = DoubleArray(totalSamples)

            notesHz.forEachIndexed { idx, freq ->
                val startSample = idx * SAMPLE_RATE * noteSpacingMs / 1000
                val durSamples = SAMPLE_RATE * noteDurationMs / 1000
                for (j in 0 until durSamples) {
                    val pos = startSample + j
                    if (pos >= totalSamples) break
                    val t = j.toDouble() / SAMPLE_RATE
                    val attack = (j.toDouble() / (SAMPLE_RATE * 0.004).coerceAtLeast(1.0)).coerceAtMost(1.0)
                    val env = attack * exp(-28.0 * t)
                    val tone = sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * (freq * 2.0) * t) * 0.22
                    mix[pos] += tone * env * amplitude
                }
            }

            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val clamped = mix[i].coerceIn(-0.98, 0.98)
                out[i] = (clamped * Short.MAX_VALUE).roundToInt().toShort()
            }
            return out
        }

        fun encodePcm16MonoWav(samples: ShortArray, sampleRate: Int): ByteArray {
            val dataSize = samples.size * 2
            val byteRate = sampleRate * 2
            val out = ByteArrayOutputStream(44 + dataSize)

            fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
            fun writeIntLE(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF)
                out.write((v shr 24) and 0xFF)
            }
            fun writeShortLE(v: Int) {
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }

            writeAscii("RIFF")
            writeIntLE(36 + dataSize)
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(1)
            writeIntLE(sampleRate)
            writeIntLE(byteRate)
            writeShortLE(2)
            writeShortLE(16)
            writeAscii("data")
            writeIntLE(dataSize)
            for (s in samples) {
                writeShortLE(s.toInt())
            }
            return out.toByteArray()
        }
    }

    private val appContext = context.applicationContext
    private val soundEnabled = AtomicBoolean(loadSoundEnabled())
    private val volumeLevel = AtomicInteger(loadVolumeLevel())
    private val count = SfxType.entries.size
    private val soundIdArray = AtomicIntegerArray(count)
    private val readyArray = AtomicIntegerArray(count) // 1 = ready

    @Volatile
    private var soundPool: SoundPool? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    init {
        initSoundPoolAsync()
    }

    private fun initSoundPoolAsync() {
        ioExecutor.execute {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build()
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        for (i in 0 until count) {
                            if (soundIdArray.get(i) == sampleId) {
                                readyArray.set(i, 1)
                                break
                            }
                        }
                    }
                }
                soundPool = pool

                val cacheDir = appContext.cacheDir ?: return@execute
                SfxType.entries.forEach { type ->
                    try {
                        val file = File(cacheDir, type.fileName)
                        if (!file.exists() || file.length() < 44L) {
                            FileOutputStream(file).use { fos ->
                                fos.write(buildWavForType(type))
                            }
                        }
                        val sid = pool.load(file.absolutePath, 1)
                        soundIdArray.set(type.ordinal, sid)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed preparing sound ${type.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SoundPool init failed; running silent fallback", e)
                soundPool = null
            }
        }
    }

    fun playClick() = play(SfxType.KEY_TAP, pitch = 1.0f)

    fun playDigitClick(digitLabel: String) {
        val pitch = when (digitLabel) {
            "1" -> 0.96f
            "2" -> 1.00f
            "3" -> 1.04f
            "4" -> 0.98f
            "5" -> 1.02f
            "6" -> 1.06f
            "7" -> 1.00f
            "8" -> 1.04f
            "9" -> 1.08f
            "0" -> 0.92f
            "00" -> 0.90f
            else -> 1.0f
        }
        play(SfxType.KEY_TAP, pitch = pitch)
    }

    fun playSoftTap() = play(SfxType.KEY_SOFT, pitch = 1.0f)

    fun playDeleteTap() = play(SfxType.KEY_DELETE, pitch = 1.0f)

    fun playCopy() = play(SfxType.COPY_CHIME, pitch = 1.0f)

    fun playSuccess() = play(SfxType.SUCCESS_CHIME, pitch = 1.0f)

    fun playError() = play(SfxType.ERROR_THUD, pitch = 1.0f)

    fun playNotify() = play(SfxType.COPY_CHIME, pitch = 1.08f)

    fun playWinnerFanfare() = play(SfxType.WINNER_FANFARE, pitch = 1.0f)

    fun isSoundEnabled(): Boolean = soundEnabled.get()

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled.set(enabled)
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, enabled)
                .apply()
        } catch (_: Exception) {}
    }

    fun getSoundVolumeLevel(): Int = volumeLevel.get()

    fun setSoundVolumeLevel(level: Int) {
        val safe = level.coerceIn(0, 2)
        volumeLevel.set(safe)
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SOUND_VOLUME_LEVEL, safe)
                .apply()
        } catch (_: Exception) {}
    }

    private fun currentGain(): Float = when (volumeLevel.get()) {
        0 -> 0.42f
        2 -> 1.00f
        else -> 0.76f
    }

    private fun loadSoundEnabled(): Boolean = try {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND_ENABLED, true)
    } catch (_: Exception) {
        true
    }

    private fun loadVolumeLevel(): Int = try {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SOUND_VOLUME_LEVEL, 1)
            .coerceIn(0, 2)
    } catch (_: Exception) {
        1
    }

    private fun play(type: SfxType, pitch: Float) {
        if (!soundEnabled.get()) return
        val pool = soundPool ?: return
        val idx = type.ordinal
        if (readyArray.get(idx) != 1) return
        val sid = soundIdArray.get(idx)
        if (sid <= 0) return
        try {
            val vol = currentGain()
            pool.play(sid, vol, vol, 1, 0, pitch)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (_: Exception) {}
        soundPool = null
        for (i in 0 until count) {
            soundIdArray.set(i, 0)
            readyArray.set(i, 0)
        }
    }
}
