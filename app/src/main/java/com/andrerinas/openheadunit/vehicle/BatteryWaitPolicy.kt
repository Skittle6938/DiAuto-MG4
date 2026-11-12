package com.andrerinas.openheadunit.vehicle

/**
 * Whether Settings > Car shows "waiting for the car" instead of the battery figures.
 *
 * Right after MG4Link starts, the car has not given its battery type yet and the resolver falls
 * back on the 64 kWh pack: the version, capacity and health shown would then be wrong for a while
 * (a 51 kWh car showed 75 % instead of 94 %). Some cars never give the type (issue #8): once the
 * read has failed, or [MAX_WAIT_MS] after the start, the default figures are shown as before.
 * Only the display changes; what Maps and ABRP receive is left to the resolver.
 */
object BatteryWaitPolicy {
    const val MAX_WAIT_MS = 120_000L

    fun waiting(
        netSource: BatteryCapacityResolver.Source,
        packChosenInMg4Dash: Boolean,
        readFailure: String?,
        sinceStartMs: Long,
    ): Boolean = netSource == BatteryCapacityResolver.Source.DEFAULT &&
        !packChosenInMg4Dash &&
        readFailure == null &&
        sinceStartMs in 0 until MAX_WAIT_MS
}
