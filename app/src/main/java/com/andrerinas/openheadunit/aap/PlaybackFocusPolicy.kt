package com.andrerinas.openheadunit.aap

/**
 * Whether to take system audio focus away from other players on the head unit while an AA audio
 * channel plays ([shouldAcquire]).
 *
 * Taking focus exists so a *local* player — typically the car's FM radio — pauses while AA audio
 * plays and resumes when it stops. It backfires when the other player is the head unit's Bluetooth
 * A2DP sink: AOSP's `A2dpSinkStreamHandler` answers the focus loss with an AVRCP passthrough PAUSE
 * to the source device, and that source is the very phone feeding the AA stream. On the dynamic
 * path the phone pauses, Android Auto tears the audio sink down, we release focus, the phone
 * resumes, and the whole thing repeats every few seconds. Nothing in the focus API distinguishes
 * the two cases, so this decides it from context instead.
 *
 * Pure and unit-tested; the I/O and the runtime bookkeeping live in [AapAudio] and [AapService].
 */
object PlaybackFocusPolicy {

    /** User override for the automatic behaviour. Stored as an ordinal in settings. */
    enum class Mode(val value: Int) {
        /** Take focus only when nothing suggests it will silence the phone we are projecting. */
        AUTO(0),

        /** Always take focus. For units where the Bluetooth probe answers wrongly. */
        ALWAYS(1),

        /** Never take focus. */
        NEVER(2);

        companion object {
            private val map = values().associateBy(Mode::value)
            fun fromInt(value: Int): Mode = map[value] ?: AUTO
        }
    }

    /**
     * @param isAudioChannel   the channel is one of AUDIO / AUDIO1 / AUDIO2.
     * @param btMediaLinkActive a Bluetooth media (A2DP) link to this head unit is up, so the
     *                          "other player" we would silence is very likely the phone itself.
     *                          Callers that cannot tell should pass `true`: a car radio playing over
     *                          AA is an annoyance, silence is a broken app.
     * @param selfDefeatingLatched we already observed the phone cut its own audio right after we
     *                          took focus, more than once this session.
     */
    /**
     * Guidance, assistant and notification audio ask with MAY_DUCK or a short transient, which
     * neither stops an A2DP stream for good nor is what the latch measures; without focus the car
     * radio simply covers them. So they always ask, unless the driver chose NEVER.
     */
    fun speechMode(mode: Mode, isSpeech: Boolean): Mode =
        if (isSpeech && mode == Mode.AUTO) Mode.ALWAYS else mode

    fun shouldAcquire(
        mode: Mode,
        audioSinkEnabled: Boolean,
        isAudioChannel: Boolean,
        btMediaLinkActive: Boolean,
        selfDefeatingLatched: Boolean
    ): Boolean {
        if (!audioSinkEnabled || !isAudioChannel) return false

        return when (mode) {
            Mode.NEVER -> false
            Mode.ALWAYS -> true
            Mode.AUTO -> !btMediaLinkActive && !selfDefeatingLatched
        }
    }

    /**
     * Whether an audio channel that stopped this soon after we took focus should count towards the
     * latch. Measured on the reporting unit, the phone paused within 60-660 ms of the grant and
     * Android Auto dropped the sink 3.4-4.1 s later; a track a listener actually chose to stop
     * lasts far longer than the window.
     *
     * Only the media channel qualifies — speech and system-sound channels are short by nature, so
     * counting them would latch on a single navigation prompt.
     */
    fun countsAsSelfDefeating(isMediaChannel: Boolean, msSinceAcquire: Long): Boolean =
        isMediaChannel && msSinceAcquire in 0L until SELF_DEFEATING_WINDOW_MS

    /** Consecutive self-defeating stops before we stop taking focus for the rest of the session. */
    const val SELF_DEFEATING_LIMIT = 2

    const val SELF_DEFEATING_WINDOW_MS = 5_000L
}
