package com.andrerinas.openheadunit.aap

import com.evsuite.hardware.FirmwareInfo

/**
 * The decisions around a voice request (issue #16), kept apart from the audio and mic I/O in
 * [AapAudio], [AapControlMedia], decoder/MicRecorder and carkey/CarKeyBroadcastReceiver so they
 * can be unit-tested: each one broke at least once on a car.
 *
 * Pure: times are elapsedRealtime() values passed in by the caller.
 */
object VoiceSessionPolicy {

    /** The phone stops the music stream this long at most before it announces the voice session. */
    const val MUSIC_STOP_BEFORE_VOICE_MS = 1_500L

    /** After the voice session, how long the phone has to restart the music by itself. */
    const val MUSIC_BACK_CHECK_MS = 1_500L

    /**
     * The phone closing the mic this soon is not the driver: nobody speaks and stops within
     * 0.4 s. It is what Android does when the Google app may not record (phone locked since
     * Android Auto connected).
     */
    const val PHONE_REFUSED_MIC_MS = 400L

    /** The mic warnings (silent car mic, phone to unlock) are shown at most this often. */
    const val MIC_WARNING_EVERY_MS = 10 * 60_000L

    /**
     * Whether DiAuto holds the assistant audio focus while the mic is open. Only on SWI68, the
     * cars with "Hello MG": it makes the car's own assistant free the cabin mic. On SWI69 it kept
     * the answer and the music from coming back (2.2.3-beta5).
     */
    fun assistantFocusWanted(generation: FirmwareInfo.Gen): Boolean =
        generation == FirmwareInfo.Gen.SWI68

    /**
     * Voice session start: whether the music counts as playing before the assistant, so it is
     * given back afterwards. The phone stops the music just before announcing the session, so a
     * stop less than [MUSIC_STOP_BEFORE_VOICE_MS] ago counts too. Once remembered, it stays until
     * the check after the session (a second START does not forget it).
     *
     * @param lastMusicStopMs time of the last media channel stop, 0 if none this session
     */
    fun musicBeforeVoice(
        alreadyRemembered: Boolean,
        mediaPlaying: Boolean,
        nowMs: Long,
        lastMusicStopMs: Long
    ): Boolean = alreadyRemembered || mediaPlaying || nowMs - lastMusicStopMs < MUSIC_STOP_BEFORE_VOICE_MS

    /**
     * [MUSIC_BACK_CHECK_MS] after the session: DiAuto gives the phone the audio focus and sends
     * play only when the music was playing before and has not come back by itself.
     */
    fun musicResumeWanted(musicBeforeVoice: Boolean, mediaPlaying: Boolean): Boolean =
        musicBeforeVoice && !mediaPlaying

    /** How long the mic stayed open, or -1 when it was never seen opening. */
    fun micOpenDuration(openedAtMs: Long, nowMs: Long): Long =
        if (openedAtMs == 0L) -1L else nowMs - openedAtMs

    /** The phone closed the mic at once: the driver is told to unlock the phone once. */
    fun phoneRefusedMic(openMs: Long): Boolean = openMs in 0L until PHONE_REFUSED_MIC_MS

    /** Whether a mic warning may be shown now, given when the last one of its kind was shown (0 = never). */
    fun micWarningDue(lastWarningMs: Long, nowMs: Long): Boolean =
        lastWarningMs == 0L || nowMs - lastWarningMs >= MIC_WARNING_EVERY_MS

    /**
     * The steering wheel voice key goes to the car's own assistant while it listens, as stock
     * AllGo does: Android Auto's assistant would only get a silent mic. Decided on the press and
     * kept for the release, so both halves of the key go to the same assistant.
     *
     * @param carAssistantActive the car's voice command focus, null when the car can't tell
     * @param leftOnPress what the press decided, used for the release
     */
    fun voiceKeyLeftToCar(isDown: Boolean, carAssistantActive: Boolean?, leftOnPress: Boolean): Boolean =
        if (isDown) carAssistantActive == true else leftOnPress

    /**
     * On SWI68, the cars with their own assistant, stock AllGo leaves a short press of the voice
     * key to the car's assistant and opens Android Auto's on a long press. Sending Android Auto
     * the short press too opened both at once (2.2.3-beta5, issue #16).
     */
    fun voiceKeyLongPressOnly(generation: FirmwareInfo.Gen): Boolean =
        generation == FirmwareInfo.Gen.SWI68

    enum class VoiceKeySend { NONE, PRESS, RELEASE }

    /**
     * Long-press-only voice key: Android Auto gets the press once the car reports the key held,
     * and the release that ends it. A short press sends nothing.
     *
     * @param pressSent whether this press already went to Android Auto
     */
    fun longPressVoiceKey(isDown: Boolean, isLong: Boolean, pressSent: Boolean): VoiceKeySend = when {
        isDown && isLong && !pressSent -> VoiceKeySend.PRESS
        !isDown && pressSent -> VoiceKeySend.RELEASE
        else -> VoiceKeySend.NONE
    }
}
