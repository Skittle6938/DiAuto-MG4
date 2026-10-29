package com.andrerinas.openheadunit.decoder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 16 kHz → 48 kHz mono upsampler for Android Auto's voice channels: a windowed-sinc low-pass in
 * polyphase form, cut just under the 8 kHz the voice carries.
 *
 * Both earlier paths left the images of the voice band in the output: the mixer interpolated
 * linearly, and a 16 kHz AudioTrack left the job to the car's resampler. Either way the prompts
 * sounded harsh and metallic. Keeps its history across calls, so chunk joins are seamless.
 */
internal class Upsampler3x {

    private val history = FloatArray(TAPS_PER_PHASE)

    /** Writes `count * 3` samples into [out] and returns that number. */
    fun process(input: ShortArray, count: Int, out: ShortArray): Int {
        var o = 0
        for (i in 0 until count) {
            // Shift the newest sample in; history[0] is x[k].
            System.arraycopy(history, 0, history, 1, TAPS_PER_PHASE - 1)
            history[0] = input[i].toFloat()
            for (phase in 0 until 3) {
                var acc = 0f
                val taps = PHASES[phase]
                for (m in 0 until TAPS_PER_PHASE) acc += taps[m] * history[m]
                out[o++] = acc.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return o
    }

    fun reset() = history.fill(0f)

    companion object {
        private const val TAPS_PER_PHASE = 16
        private const val TAPS = TAPS_PER_PHASE * 3

        /** Filter taps split by output phase: PHASES[p][m] = h[p + 3m]. */
        private val PHASES: Array<FloatArray> = run {
            val cutoff = 7_400.0 / 48_000.0 // normalized to the output rate
            val center = (TAPS - 1) / 2.0
            val h = DoubleArray(TAPS) { n ->
                val x = n - center
                val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                val window = 0.42 - 0.5 * cos(2 * PI * n / (TAPS - 1)) + 0.08 * cos(4 * PI * n / (TAPS - 1))
                sinc * window
            }
            // Unity gain per phase after zero-stuffing by 3.
            val sum = h.sum()
            Array(3) { p -> FloatArray(TAPS_PER_PHASE) { m -> (h[p + 3 * m] * 3.0 / sum).toFloat() } }
        }
    }
}
