package com.andrerinas.openheadunit.decoder

/**
 * Where prompts and the assistant (AUDIO1/AUDIO2) play on the MG4. Media (default): with the music,
 * on all speakers, but silent while the radio is the car's source. Navigation: the car's own
 * navigation channel, mixed over any source including the radio, which may sound thinner.
 * Set from Settings.guidanceOnNavigationChannel when a session's audio starts.
 */
object SpeechRoute {
    @Volatile
    var navigationChannel: Boolean = false
}
