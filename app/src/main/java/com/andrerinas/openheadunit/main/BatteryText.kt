package com.andrerinas.openheadunit.main

import android.content.Context
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver

// Battery rows of Settings > Car (DaSettingsActivity), kept when the old battery screen went.

/** "61.0 kWh · automatic (64 kWh battery by default)", or the value set by hand. */
internal fun batteryCapacityText(context: Context, resolved: BatteryCapacityResolver.Resolved): String {
    val kwh = context.getString(R.string.battery_value_kwh, resolved.netKwh)
    return if (resolved.netSource == BatteryCapacityResolver.Source.MANUAL) {
        context.getString(R.string.battery_manual_value, kwh)
    } else {
        context.getString(R.string.battery_auto_value, kwh, batteryCapacitySource(context, resolved))
    }
}

internal fun batteryCapacitySource(context: Context, resolved: BatteryCapacityResolver.Resolved): String =
    when (resolved.netSource) {
        BatteryCapacityResolver.Source.CAR -> context.getString(R.string.battery_source_car)
        else -> context.getString(
            R.string.battery_source_default_pack, resolved.pack ?: BatteryCapacityResolver.DEFAULT_PACK)
    }

internal fun batterySohText(context: Context, resolved: BatteryCapacityResolver.Resolved): String =
    when (resolved.sohSource) {
        BatteryCapacityResolver.Source.MANUAL -> context.getString(
            R.string.battery_soh_manual_value,
            context.getString(R.string.battery_value_percent, formatBatteryDecimal(resolved.sohPercent)),
        )
        BatteryCapacityResolver.Source.MEASURED -> context.getString(
            R.string.battery_auto_value,
            context.getString(R.string.battery_value_percent,
                String.format(java.util.Locale.getDefault(), "%.1f", resolved.sohPercent)),
            context.getString(R.string.battery_soh_source_measured),
        )
        // No figure to show yet: Maps is told BatteryCapacityResolver.DEFAULT_SOH meanwhile.
        else -> context.getString(
            R.string.battery_soh_measuring,
            com.andrerinas.openheadunit.vehicle.BatteryCapacityMeter.progressPercent(context).toInt(),
            com.andrerinas.openheadunit.vehicle.BatteryCapacityMeter.MIN_DROP.toInt(),
        )
    }

/** 98.75 → "98,75" in French; up to two decimals, as typed. */
private fun formatBatteryDecimal(value: Float): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply {
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(value.toDouble())
