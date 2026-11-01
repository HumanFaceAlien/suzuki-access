package com.adithyapaib.stromx

import com.adithyapaib.stromx.garage.DueState
import com.adithyapaib.stromx.garage.FuelMath
import com.adithyapaib.stromx.garage.FuelRecord
import com.adithyapaib.stromx.garage.MaintenanceItem
import com.adithyapaib.stromx.garage.MaintenanceMath
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarageMathTest {

    private val utc = ZoneOffset.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()
    private val dayMs = 86_400_000L

    private fun fill(id: Long, odo: Int, litres: Double, cost: Double, full: Boolean = true, at: Long = day(2026, 10, 1) + id) =
        FuelRecord(id, at, odo, litres, cost, full)

    @Test
    fun economyIsMeasuredFullToFull() {
        val records = listOf(
            fill(1, 12_000, 10.0, 1_050.0),
            fill(2, 12_400, 10.0, 1_050.0)
        )
        val segments = FuelMath.segments(records)
        assertEquals(1, segments.size)
        assertEquals(40.0, segments[0].kmPerLitre, 1e-9)
        assertEquals(1_050.0 / 400, segments[0].costPerKm, 1e-9)
    }

    @Test
    fun partialFillsJoinTheSegmentTheyBelongTo() {
        val records = listOf(
            fill(1, 12_000, 10.0, 1_000.0),
            fill(2, 12_200, 4.0, 400.0, full = false),
            fill(3, 12_450, 6.0, 600.0)
        )
        val segment = FuelMath.segments(records).single()
        assertEquals(450, segment.distanceKm)
        assertEquals(10.0, segment.litres, 1e-9)
        assertEquals(45.0, segment.kmPerLitre, 1e-9)
    }

    @Test
    fun fillsBeforeTheFirstFullTankAreSpendButNotEconomy() {
        val records = listOf(
            fill(1, 11_900, 3.0, 300.0, full = false),
            fill(2, 12_000, 10.0, 1_000.0)
        )
        val summary = FuelMath.summary(records, day(2026, 10, 15), utc)
        assertEquals(0, FuelMath.segments(records).size)
        assertNull(summary.avgKmPerLitre)
        assertEquals(1_300.0, summary.totalCost, 1e-9)
        assertEquals(100.0, summary.avgPricePerLitre ?: 0.0, 1e-9)
    }

    @Test
    fun monthlySpendCoversEmptyMonths() {
        val records = listOf(
            fill(1, 12_000, 10.0, 1_000.0, at = day(2026, 8, 3)),
            fill(2, 12_300, 8.0, 800.0, at = day(2026, 10, 2)),
            fill(3, 12_600, 9.0, 900.0, at = day(2026, 10, 20))
        )
        val spend = FuelMath.monthlySpend(records, day(2026, 10, 30), months = 3, zone = utc)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), spend.map { it.month })
        assertEquals(listOf(1_000.0, 0.0, 1_700.0), spend.map { it.cost })
    }

    @Test
    fun maintenanceCountsDownFromTheOdometer() {
        val item = MaintenanceItem(1, "Engine oil", 3_000, null, lastDoneKm = 10_000, lastDoneAtMs = 0)
        assertEquals(DueState.Ok, MaintenanceMath.status(item, 11_000, 0).state)
        val soon = MaintenanceMath.status(item, 12_800, 0)
        assertEquals(DueState.Soon, soon.state)
        assertEquals(200, soon.remainingKm)
        val due = MaintenanceMath.status(item, 13_120, 0)
        assertEquals(DueState.Due, due.state)
        assertEquals(-120, due.remainingKm)
    }

    @Test
    fun whicheverLimitComesFirstWins() {
        val now = day(2026, 10, 30)
        val item = MaintenanceItem(1, "Oil", 3_000, 180, lastDoneKm = 12_000, lastDoneAtMs = now - 179 * dayMs)
        val status = MaintenanceMath.status(item, 12_100, now)
        assertEquals(1, status.remainingDays)
        assertEquals(DueState.Soon, status.state)
    }

    @Test
    fun neverDoneHasNothingToCountFrom() {
        val item = MaintenanceItem(1, "Chain", 500, null, lastDoneKm = null, lastDoneAtMs = null)
        assertEquals(DueState.NotStarted, MaintenanceMath.status(item, 12_000, 0).state)
    }
}
