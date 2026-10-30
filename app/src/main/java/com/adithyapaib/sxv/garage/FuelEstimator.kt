package com.adithyapaib.sxv.garage

import kotlin.math.abs
import kotlin.math.max

/** The moment the bike's fuel gauge stepped down to [toBars], as seen over the link. */
data class BarDrop(val odometerKm: Int, val toBars: Int, val atMs: Long)

/** What the current estimate is counted from, most precise first. */
enum class EstimateBasis {
    /** A learned bar level, from the most recent gauge step since the last full tank. */
    BarCheckpoint,
    /** Counted down from the last full tank at your measured km per litre. */
    FullTank,
    /** No usable economy yet: the middle of what the current bar can mean. */
    BarsOnly
}

data class FuelPoint(val odometerKm: Int, val litres: Double, val kind: Kind) {
    enum class Kind { Fill, BarDrop, Now }
}

data class FuelEstimate(
    val litres: Double,
    /** Honest error margin in litres; shrinks as fills and bar drops accumulate. */
    val uncertaintyLitres: Double,
    /** Null until two full tanks give a measured economy. */
    val rangeKm: Int?,
    val kmPerLitre: Double?,
    val capacityLitres: Double,
    val basis: EstimateBasis,
    /** Distance since whatever the estimate is counted from. */
    val kmSinceAnchor: Int?,
    /** Bar the anchor stepped down to, when [basis] is [EstimateBasis.BarCheckpoint]. */
    val anchorBars: Int?,
    /** Litres at which the gauge drops to bar 1..5 (index 0..4); null where not learned yet. */
    val learnedBarLevels: List<Double?>,
    /** Bar drops that could be turned into calibration samples. */
    val calibrationSamples: Int,
    /** Fuel left since the last full tank: the fill, each gauge step, and now. */
    val history: List<FuelPoint>
)

/**
 * Estimates fuel left from three sources the app actually has: the fill-up log (litres and
 * odometer at each fill), the odometer, and the gauge's 1-6 bars. It never reads fuel flow:
 * the cluster link carries none.
 *
 * The countdown runs from the last full tank at the recent full-to-full economy. Each time
 * the gauge steps down, the countdown's value at that moment becomes a sample of what that
 * bar means on this bike; with [MIN_SAMPLES] samples a bar is "learned", and a later step
 * to it re-anchors the estimate so countdown drift does not build up.
 */
object FuelEstimator {
    /** Starting tank size until the rider sets theirs; check the owner's manual. */
    const val DEFAULT_TANK_LITRES = 12.0
    const val RECENT_TANKS = 3
    const val MIN_SAMPLES = 2

    /** Usable capacity: the rider's setting, never below the largest brim fill ever logged. */
    fun capacity(records: List<FuelRecord>, setting: Double?): Double =
        max(setting ?: DEFAULT_TANK_LITRES, records.filter { it.fullTank }.maxOfOrNull { it.litres } ?: 0.0)

    /** Economy over the last few full-to-full tanks, distance-weighted. */
    fun recentKmPerLitre(records: List<FuelRecord>): Double? {
        val recent = FuelMath.segments(records).sortedBy { it.endAtMs }.takeLast(RECENT_TANKS)
        val litres = recent.sumOf { it.litres }
        return if (litres > 0) recent.sumOf { it.distanceKm } / litres else null
    }

    /** The level each bar drop implies, grouped by bar (index 0 = bar 1). */
    fun barSamples(records: List<FuelRecord>, drops: List<BarDrop>, capacity: Double): List<List<Double>> {
        val sorted = FuelMath.sorted(records)
        val segments = FuelMath.segments(records)
        val fallback = recentKmPerLitre(records)
        val samples = List(5) { mutableListOf<Double>() }
        for (drop in drops) {
            if (drop.toBars !in 1..5) continue
            val full = sorted.lastOrNull { it.fullTank && it.odometerKm <= drop.odometerKm } ?: continue
            // Prefer the economy of the tank this drop happened in, once that tank is closed.
            val next = sorted.firstOrNull { it.fullTank && it.odometerKm > full.odometerKm }
            val economy = next?.let { n -> segments.firstOrNull { it.endRecordId == n.id }?.kmPerLitre } ?: fallback ?: continue
            samples[drop.toBars - 1] += countdown(sorted, full, drop.odometerKm, economy, capacity)
        }
        return samples
    }

    fun estimate(
        records: List<FuelRecord>,
        drops: List<BarDrop>,
        odometerKm: Int?,
        currentBars: Int?,
        tankSetting: Double?
    ): FuelEstimate? {
        if (odometerKm == null && currentBars == null) return null
        val capacity = capacity(records, tankSetting)
        val economy = recentKmPerLitre(records)
        val samples = barSamples(records, drops, capacity)
        val learned = samples.map { if (it.size >= MIN_SAMPLES) median(it) else null }
        fun level(bar: Int): Double = when {
            bar <= 0 -> 0.0
            bar >= 6 -> capacity
            else -> learned[bar - 1] ?: capacity * bar / 6.0
        }
        val sorted = FuelMath.sorted(records)
        val full = odometerKm?.let { odo -> sorted.lastOrNull { it.fullTank && it.odometerKm <= odo } }

        val checkpoint = if (odometerKm != null && full != null && economy != null) {
            drops
                .filter { it.odometerKm >= full.odometerKm && it.odometerKm <= odometerKm && it.toBars in 1..5 }
                .maxByOrNull { it.odometerKm }
                ?.takeIf { learned[it.toBars - 1] != null }
        } else {
            null
        }

        var basis: EstimateBasis
        var litres: Double
        var uncertainty: Double
        var sinceKm: Int? = null
        when {
            checkpoint != null && odometerKm != null && economy != null -> {
                val bar = checkpoint.toBars
                val partials = sorted
                    .filter { !it.fullTank && it.odometerKm > checkpoint.odometerKm && it.odometerKm <= odometerKm }
                    .sumOf { it.litres }
                sinceKm = odometerKm - checkpoint.odometerKm
                val burned = sinceKm / economy
                litres = level(bar) + partials - burned
                uncertainty = spread(samples[bar - 1]) + 0.3 + burned * 0.05
                basis = EstimateBasis.BarCheckpoint
            }
            full != null && odometerKm != null && economy != null -> {
                sinceKm = odometerKm - full.odometerKm
                litres = countdown(sorted, full, odometerKm, economy, capacity)
                // About 5% of what has been burned, since economy varies with how you ride.
                uncertainty = 0.3 + (sinceKm / economy) * 0.05
                basis = EstimateBasis.FullTank
            }
            currentBars != null && currentBars in 1..6 -> {
                val low = level(currentBars - 1)
                val high = level(currentBars)
                litres = (low + high) / 2
                uncertainty = (high - low) / 2
                basis = EstimateBasis.BarsOnly
            }
            else -> return null
        }

        // The gauge is still evidence: once both edges of the current bar are learned, the
        // estimate may not leave the band that bar stands for.
        if (currentBars != null && currentBars in 1..6 && basis != EstimateBasis.BarsOnly) {
            val lowKnown = currentBars == 1 || learned[currentBars - 2] != null
            val highKnown = currentBars == 6 || learned[currentBars - 1] != null
            if (lowKnown && highKnown) litres = litres.coerceIn(level(currentBars - 1), level(currentBars))
        }
        litres = litres.coerceIn(0.0, capacity)
        uncertainty = uncertainty.coerceIn(0.2, capacity / 2)

        val history = if (full != null && odometerKm != null && economy != null) {
            buildList {
                add(FuelPoint(full.odometerKm, capacity, FuelPoint.Kind.Fill))
                drops
                    .filter { it.odometerKm > full.odometerKm && it.odometerKm < odometerKm }
                    .sortedBy { it.odometerKm }
                    .forEach { add(FuelPoint(it.odometerKm, countdown(sorted, full, it.odometerKm, economy, capacity), FuelPoint.Kind.BarDrop)) }
                if (odometerKm > full.odometerKm) add(FuelPoint(odometerKm, litres, FuelPoint.Kind.Now))
            }
        } else {
            emptyList()
        }

        return FuelEstimate(
            litres = litres,
            uncertaintyLitres = uncertainty,
            rangeKm = economy?.let { (litres * it).toInt() },
            kmPerLitre = economy,
            capacityLitres = capacity,
            basis = basis,
            kmSinceAnchor = sinceKm,
            anchorBars = checkpoint?.toBars,
            learnedBarLevels = learned,
            calibrationSamples = samples.sumOf { it.size },
            history = history
        )
    }

    /** Litres in the tank at [odometerKm], counted down from the full fill [full]. */
    private fun countdown(sorted: List<FuelRecord>, full: FuelRecord, odometerKm: Int, kmPerLitre: Double, capacity: Double): Double {
        val partials = sorted
            .filter { !it.fullTank && it.odometerKm > full.odometerKm && it.odometerKm <= odometerKm }
            .sumOf { it.litres }
        val burned = (odometerKm - full.odometerKm) / kmPerLitre
        return (capacity + partials - burned).coerceIn(0.0, capacity)
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Median absolute deviation: how much this bar's samples disagree. */
    private fun spread(values: List<Double>): Double {
        if (values.size < 2) return 0.5
        val m = median(values)
        return median(values.map { abs(it - m) })
    }
}
