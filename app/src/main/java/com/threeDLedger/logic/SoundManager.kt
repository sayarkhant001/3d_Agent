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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * App-wide Studio-Synthesized Tactile Sound Manager
 * ─────────────────────────────────────────────────
 * Generates warm, zero-asset 16-bit PCM acoustic waveforms (wooden key clicks,
 * bamboo soft taps, double pearl copy chimes, emerald-gold ledger success
 * arpeggios, muted thud deletes, and celebratory winner fanfare) and plays
 * them polyphonically with ultra-low latency via `SoundPool`.
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
        private const val SAMPLE_RATE = 22050

        @Volatile
        private var instance: SoundManager? = null

        @JvmStatic
        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Pure-Kotlin PCM 16-bit WAV synthesis (also unit-tested directly without Android framework).
         */
        fun buildWavForType(type: SfxType): ByteArray {
            val samples = when (type) {
                // Crisp, soothing walnut/ivory mechanical keycap click (16ms)
                SfxType.KEY_TAP -> synthesizePercussiveClick(
                    durationMs = 18,
                    startFreq = 860.0,
                    endFreq = 290.0,
                    bodyFreq = 195.0,
                    amplitude = 0.65
                )
                // Gentle bamboo/marimba navigation tap (34ms)
                SfxType.KEY_SOFT -> synthesizePluck(
                    durationMs = 36,
                    freqs = doubleArrayOf(540.0, 1080.0),
                    weights = doubleArrayOf(0.75, 0.25),
                    decayRate = 90.0,
                    amplitude = 0.55
                )
                // Soft low woody thump for delete/backspace/clear (26ms)
                SfxType.KEY_DELETE -> synthesizePercussiveClick(
                    durationMs = 26,
                    startFreq = 310.0,
                    endFreq = 135.0,
                    bodyFreq = 120.0,
                    amplitude = 0.62
                )
                // Bright double pearl chime for Copy / Share actions (95ms)
                SfxType.COPY_CHIME -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(659.25, 987.77), // E5 -> B5
                    noteSpacingMs = 36,
                    noteDurationMs = 58,
                    amplitude = 0.55
                )
                // Warm Emerald-Gold ascending triad chime for Voucher Save / Approval (210ms)
                SfxType.SUCCESS_CHIME -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50), // C5 E5 G5 C6
                    noteSpacingMs = 42,
                    noteDurationMs = 95,
                    amplitude = 0.58
                )
                // Gentle two-beat low wooden alert for validation/banned warning (175ms)
                SfxType.ERROR_THUD -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(220.0, 164.81), // A3 -> E3
                    noteSpacingMs = 75,
                    noteDurationMs = 90,
                    amplitude = 0.68
                )
                // Festive bright fanfare when a 3D Winning Number is declared (310ms)
                SfxType.WINNER_FANFARE -> synthesizeArpeggio(
                    notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51), // C5..E6
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
                    // Fundamental + gentle octave chime harmonic
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
            writeIntLE(16)            // PCM chunk size
            writeShortLE(1)           // PCM format
            writeShortLE(1)           // Mono
            writeIntLE(sampleRate)
            writeIntLE(byteRate)
            writeShortLE(2)           // Block align
            writeShortLE(16)          // Bits per sample
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
    private val soundIds = HashMap<SfxType, Int>()
    private val readyFlags = HashMap<Int, Boolean>()
    private var soundPool: SoundPool? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    synchronized(readyFlags) { readyFlags[sampleId] = true }
                }
            }
            soundPool = pool

            ioExecutor.execute {
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
                        synchronized(soundIds) { soundIds[type] = sid }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed preparing sound ${type.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SoundPool init failed; running silent fallback", e)
            soundPool = null
        }
    }

    // ── Public Sound Effects API ─────────────────────────────────────────────

    /** Crisp mechanical key tap for primary action buttons & general clicks. */
    fun playClick() = play(SfxType.KEY_TAP, pitch = 1.0f)

    /** Subtle melodic variation across digits so fast keypad entry feels physical & pleasant. */
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

    /** Gentle bamboo tap for navigation cards, menus, filters & tabs. */
    fun playSoftTap() = play(SfxType.KEY_SOFT, pitch = 1.0f)

    /** Low muted wood tap for Backspace, Clear & Delete actions. */
    fun playDeleteTap() = play(SfxType.KEY_DELETE, pitch = 1.0f)

    /** Delicate double chime when copying or sharing a slip. */
    fun playCopy() = play(SfxType.COPY_CHIME, pitch = 1.0f)

    /** Warm Emerald-Gold ascending triad chime for saving vouchers & confirming actions. */
    fun playSuccess() = play(SfxType.SUCCESS_CHIME, pitch = 1.0f)

    /** Low double wood thud for validation errors or banned numbers. */
    fun playError() = play(SfxType.ERROR_THUD, pitch = 1.0f)

    /** Subtle notification chime. */
    fun playNotify() = play(SfxType.COPY_CHIME, pitch = 1.08f)

    /** Celebratory gold chime when a 3D winning number is declared. */
    fun playWinnerFanfare() = play(SfxType.WINNER_FANFARE, pitch = 1.0f)

    // ── Preference management ────────────────────────────────────────────────

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

    /** 0 = Soft (တိုး), 1 = Normal (ပုံမှန်), 2 = Clear (ကျယ်) */
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

    // ── Internals ────────────────────────────────────────────────────────────

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
        val sid = synchronized(soundIds) { soundIds[type] } ?: return
        val isReady = synchronized(readyFlags) { readyFlags[sid] == true }
        if (!isReady) return
        try {
            val vol = currentGain()
            pool.play(sid, vol, vol, 1, 0, pitch)
        } catch (e: Exception) {
            Log.w(TAG, "SoundPool play failed for ${type.name}", e)
        }
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (_: Exception) {}
        soundPool = null
        synchronized(soundIds) { soundIds.clear() }
        synchronized(readyFlags) { readyFlags.clear() }
    }
}
