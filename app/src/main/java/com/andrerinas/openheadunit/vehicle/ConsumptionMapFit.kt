package com.andrerinas.openheadunit.vehicle

import org.json.JSONObject
import kotlin.math.abs

/**
 * Turns MG4Dash's consumption map (driving consumption only, by average speed and outside
 * temperature, gradient removed) into the road load Android Auto's energy model takes:
 * F(v) = A + B·v + C·v² in N, v in m/s. Maps works out every stretch of the route from it, with its
 * own speeds and gradients; it takes no table and no value per stretch.
 *
 * 1 kWh/100 km = 3.6 MJ / 100 km = 36 N.
 */
object ConsumptionMapFit {

    data class RoadLoad(val a: Float, val b: Float, val c: Float, val points: Int, val km: Float)

    /** A cell with less than this is noise (one or two 1 km stretches). */
    private const val MIN_CELL_KM = 2f
    /** Less than this over the speeds used: not enough to beat the car's own average. */
    private const val MIN_TOTAL_KM = 10f
    /** A speed missing at the current temperature is taken from a row at most this many bins away. */
    private const val MAX_TEMP_BIN_DISTANCE = 2
    private const val N_PER_KWH_100KM = 36f

    /** Road load at [outsideC] (null: all temperatures together), or null when the map is too thin. */
    fun fit(json: String, outsideC: Float?): RoadLoad? {
        val root = JSONObject(json)
        val speedStep = root.optInt("speedStep", 10)
        val tempMin = root.optInt("tempMin", -20)
        val tempStep = root.optInt("tempStep", 5)
        val cells = root.getJSONArray("cells")
        // [speed bin][temp bin] -> (km, kWh)
        val grid = HashMap<Int, HashMap<Int, Pair<Float, Float>>>()
        for (i in 0 until cells.length()) {
            val c = cells.getJSONArray(i)
            val km = c.getDouble(2).toFloat()
            if (km < MIN_CELL_KM) continue
            grid.getOrPut(c.getInt(0)) { HashMap() }[c.getInt(1)] = km to c.getDouble(3).toFloat()
        }
        val wantedTemp = outsideC?.let { ((it - tempMin) / tempStep).toInt() }

        // One point per speed bin: the current temperature's cell, else the nearest row's.
        val points = ArrayList<Triple<Float, Float, Float>>() // v m/s, F N, weight km
        for ((speedBin, byTemp) in grid) {
            val cell = if (wantedTemp == null) {
                byTemp.values.fold(0f to 0f) { acc, p -> (acc.first + p.first) to (acc.second + p.second) }
            } else {
                byTemp.entries.filter { abs(it.key - wantedTemp) <= MAX_TEMP_BIN_DISTANCE }
                    .minByOrNull { abs(it.key - wantedTemp) }?.value
            } ?: continue
            val (km, kwh) = cell
            if (km <= 0f) continue
            val kmh = speedBin * speedStep + speedStep / 2f
            points += Triple(kmh / 3.6f, kwh / km * 100f * N_PER_KWH_100KM, km)
        }
        val totalKm = points.sumOf { it.third.toDouble() }.toFloat()
        if (points.isEmpty() || totalKm < MIN_TOTAL_KM) return null

        val speeds = points.map { it.first }
        val span = (speeds.max() - speeds.min()) * 3.6f
        // Enough spread for a curve; otherwise a flat force, which is what the car's average gives too.
        val full = if (points.size >= 4 && span >= 40f) solve(points, quadratic = true, linear = true) else null
        val aero = if (full == null && points.size >= 2 && span >= 20f) solve(points, quadratic = true, linear = false) else null
        val chosen = (full?.takeIf { positive(it) } ?: aero?.takeIf { positive(it) })
            ?: floatArrayOf(points.weightedMeanForce(), 0f, 0f)
        return RoadLoad(chosen[0], chosen[1], chosen[2], points.size, totalKm)
    }

    /** Resistance must stay positive at every speed Maps may use. */
    private fun positive(k: FloatArray): Boolean =
        (0..150 step 10).all { kmh -> val v = kmh / 3.6f; k[0] + k[1] * v + k[2] * v * v > 50f } && k[2] >= 0f

    private fun List<Triple<Float, Float, Float>>.weightedMeanForce(): Float =
        (sumOf { (it.second * it.third).toDouble() } / sumOf { it.third.toDouble() }).toFloat()

    /** Weighted least squares on 1, v (when [linear]) and v² (when [quadratic]); returns A, B, C. */
    private fun solve(points: List<Triple<Float, Float, Float>>, quadratic: Boolean, linear: Boolean): FloatArray? {
        val basis: List<(Double) -> Double> = buildList {
            add { _ -> 1.0 }
            if (linear) add { v -> v }
            if (quadratic) add { v -> v * v }
        }
        val n = basis.size
        val m = Array(n) { DoubleArray(n + 1) }
        for ((v, f, w) in points) {
            val row = basis.map { it(v.toDouble()) }
            for (i in 0 until n) {
                for (j in 0 until n) m[i][j] += w * row[i] * row[j]
                m[i][n] += w * row[i] * f
            }
        }
        // Gauss-Jordan with partial pivoting.
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(m[it][col]) }
            if (abs(m[pivot][col]) < 1e-9) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until n) if (r != col) {
                val k = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= k * m[col][c]
            }
        }
        val x = DoubleArray(n) { m[it][n] / m[it][it] }
        return if (linear) floatArrayOf(x[0].toFloat(), x[1].toFloat(), x[2].toFloat())
        else floatArrayOf(x[0].toFloat(), 0f, x[1].toFloat())
    }
}
