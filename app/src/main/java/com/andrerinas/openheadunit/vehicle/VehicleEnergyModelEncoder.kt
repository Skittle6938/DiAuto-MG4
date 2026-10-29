package com.andrerinas.openheadunit.vehicle

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

/**
 * Wire encoder for Android Auto VehicleEnergyModel (sensor type 23/25).
 *
 * Field layout follows the audited *external* Maps schema (current/capacity Wh +
 * road-load coefficients), not the older internal reconstruction that treated
 * field 2 as Wh/km + Cd. Sending Wh/km (~150) and Cd (~0.36) as A/B/C made Maps
 * burn most of the pack over short routes (e.g. 53 km → ~14% remaining).
 *
 * Maps SoC = current_Wh / capacity_Wh.
 *
 * A fuller layout decoded from a Ford model (charging curve on field 4, charge times on field 8,
 * mass and temperature on field 1) was tried in 2.2.0-beta5 and made Maps plan short stops on slow
 * AC chargers with odd arrival levels, so it was taken back out (2026-10-29). Field 4 stays the
 * fuel and connector block; losing it may be what kept Maps off CCS fast chargers.
 */
object VehicleEnergyModelEncoder {

    private const val MIN_WH_PER_KM = 100f
    private const val MAX_WH_PER_KM = 220f
    private const val DEFAULT_WH_PER_KM = 160f // typical MG4 Long Range ballpark

    data class Snapshot(
        val capacityWh: Int,
        val currentWh: Int,
        val rangeMeters: Int,
        val batteryPercent: Double = 0.0,
        val maxChargePowerW: Int = 140_000,
        val peakMotorPowerW: Int = 150_000,
        /** The MG4 version, for its real peak DC power. */
        val variant: Mg4Variant? = null,
    )

    fun encode(snapshot: Snapshot): ByteArray {
        require(snapshot.capacityWh > 0)
        require(snapshot.currentWh > 0)
        require(snapshot.rangeMeters > 0)

        val whPerKm = derivedWhPerKm(snapshot)
        // F ≈ (Wh/km · v_kmh) / v_ms = Wh/km · 3.6 at any speed; at REF_SPEED that is
        // the constant force that yields [whPerKm] with B=C=0.
        val constantForceN = (whPerKm * 3.6f).coerceIn(200f, 900f)

        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeByteArray(1, encodeBattery(snapshot))
        cos.writeByteArray(2, encodeRoadLoad(constantForceN))
        cos.writeByteArray(4, encodeVehicleSpecs())
        cos.flush()
        return out.toByteArray()
    }

    private fun derivedWhPerKm(snapshot: Snapshot): Float {
        val fromRange = snapshot.currentWh.toFloat() / snapshot.rangeMeters.toFloat() * 1000f
        return if (fromRange.isFinite() && fromRange > 0f) {
            fromRange.coerceIn(MIN_WH_PER_KM, MAX_WH_PER_KM)
        } else {
            DEFAULT_WH_PER_KM
        }
    }

    private fun encodeBattery(snapshot: Snapshot): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        // External: 3 = current Wh, 4 = capacity Wh. Float child is uncertainty — omit it.
        cos.writeByteArray(3, encodeWattHours(snapshot.currentWh))
        cos.writeByteArray(4, encodeWattHours(snapshot.capacityWh))
        // 9 = peak motor power W, 10 = max charging rate W (not discharge).
        cos.writeUInt32(9, snapshot.peakMotorPowerW.coerceAtLeast(1))
        val maxCharge = snapshot.variant?.maxDcW ?: snapshot.maxChargePowerW
        cos.writeUInt32(10, maxCharge.coerceAtLeast(1))
        cos.flush()
        return out.toByteArray()
    }

    /**
     * Road-load: constant / linear / quadratic means (N, N·s/m, N·s²/m²).
     * Only the constant term is populated so arrival energy tracks the pack's
     * implied Wh/km without a huge v² term.
     */
    private fun encodeRoadLoad(constantForceN: Float): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeByteArray(1, encodeCoeff(constantForceN, 10f))
        cos.writeByteArray(2, encodeCoeff(0f, 0.25f))
        cos.writeByteArray(3, encodeCoeff(0f, 0.125f))
        cos.flush()
        return out.toByteArray()
    }

    private fun encodeVehicleSpecs(): ByteArray {
        val fuelEntry = ByteArrayOutputStream().also { raw ->
            val cos = CodedOutputStream.newInstance(raw)
            cos.writeInt32(1, 10) // ELECTRIC
            cos.flush()
        }.toByteArray()
        // Same connector set as Service Discovery: AC Type 2 + DC CCS2.
        val type2 = encodeConnectorType(2) // MENNEKES
        val ccs2 = encodeConnectorType(5) // COMBO_2 / CCS2
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeByteArray(1, fuelEntry)
        cos.writeByteArray(2, type2)
        cos.writeByteArray(2, ccs2)
        cos.flush()
        return out.toByteArray()
    }

    private fun encodeConnectorType(type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeInt32(1, type)
        cos.flush()
        return out.toByteArray()
    }

    private fun encodeWattHours(wattHours: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeInt32(1, wattHours)
        cos.flush()
        return out.toByteArray()
    }

    private fun encodeCoeff(mean: Float, stddev: Float): ByteArray {
        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)
        cos.writeFloat(1, mean)
        cos.writeFloat(2, stddev)
        cos.flush()
        return out.toByteArray()
    }

    /** Exposed for unit tests — Wh/km implied by the snapshot after clamping. */
    internal fun whPerKmForTest(snapshot: Snapshot): Float = derivedWhPerKm(snapshot)

    /** Exposed for unit tests — constant force (N) written into road-load field 1. */
    internal fun constantForceNForTest(snapshot: Snapshot): Float =
        (derivedWhPerKm(snapshot) * 3.6f).coerceIn(200f, 900f)
}
