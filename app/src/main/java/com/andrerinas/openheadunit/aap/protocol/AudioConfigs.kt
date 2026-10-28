package com.andrerinas.openheadunit.aap.protocol

import android.media.AudioManager
import android.util.SparseArray
import com.andrerinas.openheadunit.aap.protocol.proto.Media

import com.andrerinas.openheadunit.decoder.AudioDecoder

object AudioConfigs {
    private val audioTracks = SparseArray<Media.AudioConfiguration>(3)

    fun stream(channel: Int, separateAudioStreams: Boolean = true) : Int
    {
        if (separateAudioStreams) {
            return when(channel) {
                Channel.ID_AU1 -> AudioManager.STREAM_VOICE_CALL
                Channel.ID_AU2 -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }
        }
        return AudioManager.STREAM_MUSIC
    }

    fun get(channel: Int): Media.AudioConfiguration {
        return audioTracks.get(channel)
    }

    init {
        val audioConfig0 = Media.AudioConfiguration.newBuilder().apply {
            sampleRate = AudioDecoder.SAMPLE_RATE_HZ_48
            numberOfBits = 16
            numberOfChannels = 2
        }.build()
        audioTracks.put(Channel.ID_AUD, audioConfig0)

        // Guidance, assistant and system sounds at 48 kHz like music (was 16 kHz, the usual speech
        // rate, heard as dull on the MG4 navigation channel).
        val audioConfig1 = Media.AudioConfiguration.newBuilder().apply {
            sampleRate = AudioDecoder.SAMPLE_RATE_HZ_48
            numberOfBits = 16
            numberOfChannels = 1
        }.build()
        audioTracks.put(Channel.ID_AU1, audioConfig1)

        val audioConfig2 = Media.AudioConfiguration.newBuilder().apply {
            sampleRate = AudioDecoder.SAMPLE_RATE_HZ_48
            numberOfBits = 16
            numberOfChannels = 1
        }.build()
        audioTracks.put(Channel.ID_AU2, audioConfig2)
    }
}
