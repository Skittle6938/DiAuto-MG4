package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.VoiceSessionPolicy.MIC_WARNING_EVERY_MS
import com.andrerinas.openheadunit.aap.VoiceSessionPolicy.MUSIC_STOP_BEFORE_VOICE_MS
import com.andrerinas.openheadunit.aap.VoiceSessionPolicy.PHONE_REFUSED_MIC_MS
import com.evsuite.hardware.FirmwareInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice requests on the MG4 (issue #16, 2.2.3-beta4 and beta5). */
class VoiceSessionPolicyTest {

    // --- assistant focus: SWI68 only ---

    @Test
    fun `assistant focus is held only on SWI68, the cars with Hello MG`() {
        assertTrue(VoiceSessionPolicy.assistantFocusWanted(FirmwareInfo.Gen.SWI68))
        for (gen in FirmwareInfo.Gen.values().filter { it != FirmwareInfo.Gen.SWI68 }) {
            assertFalse("gen=$gen", VoiceSessionPolicy.assistantFocusWanted(gen))
        }
    }

    // --- music before the assistant ---

    private val now = 1_000_000L

    @Test
    fun `music playing at the voice start is remembered`() {
        assertTrue(VoiceSessionPolicy.musicBeforeVoice(false, mediaPlaying = true, nowMs = now, lastMusicStopMs = 0L))
    }

    @Test
    fun `music stopped by the phone just before the voice start counts as playing`() {
        assertTrue(VoiceSessionPolicy.musicBeforeVoice(false, false, now, lastMusicStopMs = now - 200))
        assertTrue(VoiceSessionPolicy.musicBeforeVoice(false, false, now, lastMusicStopMs = now - MUSIC_STOP_BEFORE_VOICE_MS + 1))
    }

    @Test
    fun `music stopped long before the voice start is not given back`() {
        assertFalse(VoiceSessionPolicy.musicBeforeVoice(false, false, now, lastMusicStopMs = now - MUSIC_STOP_BEFORE_VOICE_MS))
        assertFalse(VoiceSessionPolicy.musicBeforeVoice(false, false, now, lastMusicStopMs = now - 60_000))
    }

    @Test
    fun `no music this session means nothing to give back`() {
        assertFalse(VoiceSessionPolicy.musicBeforeVoice(false, false, now, lastMusicStopMs = 0L))
    }

    @Test
    fun `a second voice start keeps the music remembered by the first`() {
        assertTrue(VoiceSessionPolicy.musicBeforeVoice(true, false, now, lastMusicStopMs = 0L))
    }

    // --- music after the assistant ---

    @Test
    fun `music not back after the assistant is resumed`() {
        assertTrue(VoiceSessionPolicy.musicResumeWanted(musicBeforeVoice = true, mediaPlaying = false))
    }

    @Test
    fun `music the phone restarted by itself is left alone`() {
        assertFalse(VoiceSessionPolicy.musicResumeWanted(musicBeforeVoice = true, mediaPlaying = true))
    }

    @Test
    fun `no music before the assistant means no play sent afterwards`() {
        assertFalse(VoiceSessionPolicy.musicResumeWanted(musicBeforeVoice = false, mediaPlaying = false))
        assertFalse(VoiceSessionPolicy.musicResumeWanted(musicBeforeVoice = false, mediaPlaying = true))
    }

    @Test
    fun `the phone gets time to restart the music by itself first`() {
        assertTrue(VoiceSessionPolicy.MUSIC_BACK_CHECK_MS >= 1_000L)
    }

    // --- mic closed at once: phone to unlock ---

    @Test
    fun `mic open duration`() {
        assertEquals(-1L, VoiceSessionPolicy.micOpenDuration(openedAtMs = 0L, nowMs = now))
        assertEquals(250L, VoiceSessionPolicy.micOpenDuration(openedAtMs = now - 250, nowMs = now))
    }

    @Test
    fun `mic closed by the phone within 400 ms means the phone refused it`() {
        assertTrue(VoiceSessionPolicy.phoneRefusedMic(0L))
        assertTrue(VoiceSessionPolicy.phoneRefusedMic(120L))
        assertTrue(VoiceSessionPolicy.phoneRefusedMic(PHONE_REFUSED_MIC_MS - 1))
    }

    @Test
    fun `a normal request or an unknown open time is not a refusal`() {
        assertFalse(VoiceSessionPolicy.phoneRefusedMic(PHONE_REFUSED_MIC_MS))
        assertFalse(VoiceSessionPolicy.phoneRefusedMic(3_000L))
        assertFalse(VoiceSessionPolicy.phoneRefusedMic(-1L))
    }

    // --- mic warnings: at most every 10 minutes ---

    @Test
    fun `the first mic warning is always shown`() {
        assertTrue(VoiceSessionPolicy.micWarningDue(lastWarningMs = 0L, nowMs = now))
    }

    @Test
    fun `mic warnings are not repeated within 10 minutes`() {
        assertFalse(VoiceSessionPolicy.micWarningDue(lastWarningMs = now - 1_000, nowMs = now))
        assertFalse(VoiceSessionPolicy.micWarningDue(lastWarningMs = now - MIC_WARNING_EVERY_MS + 1, nowMs = now))
        assertTrue(VoiceSessionPolicy.micWarningDue(lastWarningMs = now - MIC_WARNING_EVERY_MS, nowMs = now))
    }

    // --- steering wheel voice key ---

    @Test
    fun `voice key goes to the car while its own assistant listens`() {
        assertTrue(VoiceSessionPolicy.voiceKeyLeftToCar(isDown = true, carAssistantActive = true, leftOnPress = false))
    }

    @Test
    fun `voice key goes to Android Auto when the car's assistant is idle or unknown`() {
        assertFalse(VoiceSessionPolicy.voiceKeyLeftToCar(isDown = true, carAssistantActive = false, leftOnPress = true))
        assertFalse(VoiceSessionPolicy.voiceKeyLeftToCar(isDown = true, carAssistantActive = null, leftOnPress = true))
    }

    @Test
    fun `the release goes where the press went`() {
        assertTrue(VoiceSessionPolicy.voiceKeyLeftToCar(isDown = false, carAssistantActive = false, leftOnPress = true))
        assertFalse(VoiceSessionPolicy.voiceKeyLeftToCar(isDown = false, carAssistantActive = true, leftOnPress = false))
    }

    // --- long-press-only voice key (SWI68) ---

    @Test
    fun `only SWI68 leaves the short press to the car's assistant`() {
        assertTrue(VoiceSessionPolicy.voiceKeyLongPressOnly(com.evsuite.hardware.FirmwareInfo.Gen.SWI68))
        assertFalse(VoiceSessionPolicy.voiceKeyLongPressOnly(com.evsuite.hardware.FirmwareInfo.Gen.SWI69))
    }

    @Test
    fun `a short press sends nothing to Android Auto`() {
        assertEquals(VoiceSessionPolicy.VoiceKeySend.NONE, VoiceSessionPolicy.longPressVoiceKey(isDown = true, isLong = false, pressSent = false))
        assertEquals(VoiceSessionPolicy.VoiceKeySend.NONE, VoiceSessionPolicy.longPressVoiceKey(isDown = false, isLong = false, pressSent = false))
    }

    @Test
    fun `a long press sends one press and its release`() {
        assertEquals(VoiceSessionPolicy.VoiceKeySend.PRESS, VoiceSessionPolicy.longPressVoiceKey(isDown = true, isLong = true, pressSent = false))
        assertEquals(VoiceSessionPolicy.VoiceKeySend.NONE, VoiceSessionPolicy.longPressVoiceKey(isDown = true, isLong = true, pressSent = true))
        assertEquals(VoiceSessionPolicy.VoiceKeySend.RELEASE, VoiceSessionPolicy.longPressVoiceKey(isDown = false, isLong = false, pressSent = true))
    }
}
