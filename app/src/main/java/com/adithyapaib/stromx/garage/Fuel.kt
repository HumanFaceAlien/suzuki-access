package com.adithyapaib.stromx.garage

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One fill-up as the rider logged it. Distances are always km: the bike's odometer is km. */
data class FuelRecord(
    val id: Long,
    val atMs: Long,
    val odometerKm: Int,
    val litres: Double,
    val cost: Double,
    /** Filled to the brim. Economy is only measured between two full-tank fills. */
    val fullTank: Boolean
) {
    val pricePerLitre: Double get() = if (litres > 0) cost / litres else 0.0
}

/** The stretch between two full-tank fills: the only honest way to measure economy. */
data class FuelSegment(
    val endRecordId: Long,
    val endAtMs: Long,
    val distanceKm: Int,
    val litres: Double,
    val cost: Double
) {
    val kmPerLitre: Double get() = distanceKm / litres
    val costPerKm: Double get() = cost / distanceKm
}

data class FuelSummary(
    val fills: Int,
    val totalCost: Double,
    val totalLitres: Double,
    /** Null until two full-tank fills bracket a measurable distance. */
    val avgKmPerLitre: Double?,
    val latestKmPerLitre: Double?,
    val avgCostPerKm: Double?,
    val avgPricePerLitre: Double?,
    val thisMonthCost: Double
)

data class MonthSpend(val month: YearMonth, val cost: Double)

object FuelMath {

    fun sorted(records: List<FuelRecord>): List<FuelRecord> =
        records.sortedWith(compareBy({ it.odometerKm }, { it.atMs }))

    /**
     * Full-to-full segments. Partial fills between two full fills add their litres and cost to
     * the segment they belong to; fills before the first full fill cannot be attributed and
     * are left out of economy (but still count toward spend).
     */
    fun segments(records: List<FuelRecord>): List<FuelSegment> {
        val result = mutableListOf<FuelSegment>()
        var lastFull: FuelRecord? = null
        var litres = 0.0
        var cost = 0.0
        for (record in sorted(records)) {
            val anchor = lastFull
            if (anchor == null) {
                if (record.fullTank) lastFull = record
                continue
            }
            litres += record.litres
            cost += record.cost
            if (record.fullTank) {
                val distance = record.odometerKm - anchor.odometerKm
                if (distance > 0 && litres > 0) {
                    result += FuelSegment(record.id, record.atMs, distance, litres, cost)
                }
                lastFull = record
                litres = 0.0
                cost = 0.0
            }
        }
        return result
    }

    fun summary(records: List<FuelRecord>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): FuelSummary {
        val segments = segments(records)
        val segDistance = segments.sumOf { it.distanceKm }
        val segLitres = segments.sumOf { it.litres }
        val segCost = segments.sumOf { it.cost }
        val totalCost = records.sumOf { it.cost }
        val totalLitres = records.sumOf { it.litres }
        val thisMonth = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        return FuelSummary(
            fills = records.size,
            totalCost = totalCost,
            totalLitres = totalLitres,
            avgKmPerLitre = if (segLitres > 0) segDistance / segLitres else null,
            latestKmPerLitre = segments.maxByOrNull { it.endAtMs }?.kmPerLitre,
            avgCostPerKm = if (segDistance > 0) segCost / segDistance else null,
            avgPricePerLitre = if (totalLitres > 0) totalCost / totalLitres else null,
            thisMonthCost = records
                .filter { YearMonth.from(Instant.ofEpochMilli(it.atMs).atZone(zone)) == thisMonth }
                .sumOf { it.cost }
        )
    }

    /** Spend for each of the last [months] calendar months, oldest first, empty months as 0. */
    fun monthlySpend(
        records: List<FuelRecord>,
        nowMs: Long,
        months: Int = 6,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<MonthSpend> {
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        val byMonth = records.groupBy { YearMonth.from(Instant.ofEpochMilli(it.atMs).atZone(zone)) }
        return (months - 1 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            MonthSpend(month, byMonth[month]?.sumOf { it.cost } ?: 0.0)
        }
    }
}
