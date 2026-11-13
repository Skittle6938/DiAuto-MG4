package com.andrerinas.openheadunit.aap

/**
 * Whether the music comes compressed (AAC) or uncompressed (PCM) in a session. PCM sounds better
 * and needs ~1.5 Mbit/s more, which 5 GHz carries without trouble (a tester, a week of daily
 * drives with no dropout). So PCM by default, except on a 2.4 GHz hotspot (test, 2026-11-11).
 * A driver who set the option keeps that choice.
 */
object MusicCodecPolicy {

    /**
     * @param choice the driver's own setting, null when never set
     * @param wireless false over USB, where bandwidth is no question
     * @param apBand the hotspot's band when known, null when it is not
     */
    fun useAac(choice: Boolean?, wireless: Boolean, apBand: ApBand?): Boolean = when {
        choice != null -> choice
        !wireless -> false
        else -> apBand == ApBand.BAND_2GHZ
    }

    /** The stored legacy band (apBand of WifiConfiguration: 0 = 2.4 GHz, 1 = 5 GHz), or null. */
    fun bandOfLegacy(apBand: Int?): ApBand? = when (apBand) {
        0 -> ApBand.BAND_2GHZ
        1 -> ApBand.BAND_5GHZ
        else -> null
    }
}

/** The choice made at service discovery, so the music decoder follows what the phone was told. */
object SessionMusicCodec {
    @Volatile var aac: Boolean = false
}
