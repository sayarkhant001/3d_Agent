package com.threeDLedger.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-wide Sound Feedback Manager
 * ─────────────────────────────────
 * Provides tactile audio feedback for UI interactions using Android's
 * built-in `ToneGenerator` (no external audio assets required).
 *
 * Usage from any Composable:
 *   val sound = SoundManager.getInstance(LocalContext.current)
 *   sound.playClick()          // on button press
 *   sound.playSuccess()        // after save/submit success
 *   sound.playError()          // after validation failure
 *
 * Respects a shared preference "app_prefs.soundEnabled" so users can
 * toggle sound feedback on/off from Settings.
 */
class SoundManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SoundManager"
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_SOUND_ENABLED = "soundEnabled"

        // Singleton instance — created lazily on first access.
        @Volatile
        private var instance: SoundManager? = null

        @JvmStatic
        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }

        // ── Stream / Volume constants ────────────────────────────────────────
        private const val STREAM_VOLUME = 80        // 0..100 for ToneGenerator
        private const val CLICK_DURATION = 80       // ms
        private const val SUCCESS_DURATION = 200    // ms
        private const val ERROR_DURATION = 250      // ms
    }

    private val appContext = context.applicationContext
    private val soundEnabled = AtomicBoolean(loadSoundEnabled())

    private var toneGenerator: ToneGenerator? = null
    private val generatorLock = Object()

    init {
        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_SYSTEM,
                STREAM_VOLUME
            )
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator init failed, sound feedback disabled", e)
            toneGenerator = null
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Short, crisp click for buttons / menu cards / keypad taps. */
    fun playClick() {
        play(ToneGenerator.TONE_PROP_BEEP, CLICK_DURATION)
    }

    /** Softer double-tap feedback for opening a details section. */
    fun playSoftTap() {
        play(ToneGenerator.TONE_PROP_BEEP2, 60)
    }

    /** Ascending chime for save / submit / print success. */
    fun playSuccess() {
        play(ToneGenerator.TONE_PROP_ACK, SUCCESS_DURATION)
    }

    /** Low buzz for validation errors / rejected input. */
    fun playError() {
        play(ToneGenerator.TONE_PROP_NACK, ERROR_DURATION)
    }

    /** Higher-pitched notification blip for a new message / result update. */
    fun playNotify() {
        play(ToneGenerator.TONE_PROP_BEEP2, 150)
    }

    // ── Preference management ────────────────────────────────────────────────

    fun isSoundEnabled(): Boolean = soundEnabled.get()

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled.set(enabled)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, enabled)
            .apply()
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun loadSoundEnabled(): Boolean {
        return try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SOUND_ENABLED, true) // default: sound ON
        } catch (e: Exception) {
            true
        }
    }

    private fun play(tone: Int, durationMs: Int) {
        if (!soundEnabled.get()) return
        synchronized(generatorLock) {
            val generator = toneGenerator ?: return
            try {
                generator.startTone(tone, durationMs)
            } catch (e: Exception) {
                // ToneGenerator can throw on some devices when called rapidly.
                // We re-create it lazily on the next call.
                Log.w(TAG, "startTone failed, will re-init next time", e)
                try { generator.release() } catch (_: Exception) {}
                toneGenerator = null
                try {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, STREAM_VOLUME)
                } catch (_: Exception) {}
            }
        }
    }

    /** Release resources when app is destroyed (optional lifecycle hook). */
    fun release() {
        synchronized(generatorLock) {
            try { toneGenerator?.release() } catch (_: Exception) {}
            toneGenerator = null
        }
    }
}
