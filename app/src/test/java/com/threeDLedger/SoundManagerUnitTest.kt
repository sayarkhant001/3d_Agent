package com.threeDLedger

import com.threeDLedger.logic.SoundManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundManagerUnitTest {

    @Test
    fun synthesizesValidWavHeadersAndAudibleWaveformsForAllSfxTypes() {
        SoundManager.SfxType.entries.forEach { type ->
            val wav = SoundManager.buildWavForType(type)
            assertTrue("WAV for ${type.name} should have header + PCM data", wav.size > 44)
            assertEquals('R'.code.toByte(), wav[0])
            assertEquals('I'.code.toByte(), wav[1])
            assertEquals('F'.code.toByte(), wav[2])
            assertEquals('F'.code.toByte(), wav[3])
            assertEquals('W'.code.toByte(), wav[8])
            assertEquals('A'.code.toByte(), wav[9])
            assertEquals('V'.code.toByte(), wav[10])
            assertEquals('E'.code.toByte(), wav[11])

            // Check that at least one PCM sample is non-zero (audible envelope generated)
            var hasNonZeroSample = false
            for (i in 44 until wav.size) {
                if (wav[i].toInt() != 0) {
                    hasNonZeroSample = true
                    break
                }
            }
            assertTrue("Waveform for ${type.name} should not be silent", hasNonZeroSample)
        }
    }
}
