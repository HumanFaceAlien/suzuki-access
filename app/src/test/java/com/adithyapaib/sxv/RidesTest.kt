package com.adithyapaib.sxv

import com.adithyapaib.sxv.ride.RideMath
import com.adithyapaib.sxv.ride.RideRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RidesTest {

    private val hourMs = 3_600_000L

    private fun active(
        id: Long = 1,
        startedAtMs: Long = 1_000L,
        startOdometerKm: Int? = 8_800,
        maxSpeedKph: Int = 0,
        startFuelBars: Int? = null
    ) = RideRecord(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = null,
        startOdometerKm = startOdometerKm,
        endOdometerKm = null,
        maxSpeedKph = maxSpeedKph,
        startFuelBars = startFuelBars,
        endFuelBars = null,
        fuelUsedLitres = null,
        fuelCost = null
    )

    @Test
    fun anActiveRideHasNoDurationDistanceOrFuelYet() {
        val ride = active()
        assertTrue(ride.isActive)
        assertNull(ride.durationMs)
        assertNull(ride.distanceKm)
        assertNull(ride.avgSpeedKph)
        assertNull(ride.fuelUsedLitres)
    }

    @Test
    fun aFinishedRideKnowsItsDistanceDurationAndAverageSpeed() {
        val ride = RideMath.finish(
            ride = active(startedAtMs = 0L, startOdometerKm = 8_800),
            endedAtMs = hourMs,
            endOdometerKm = 8_860,
            endFuelBars = 4,
            kmPerLitre = null,
            pricePerLitre = null
        )
        assertEquals(60.0, ride.distanceKm!!, 1e-9)
        assertEquals(hourMs, ride.durationMs)
        assertEquals(60.0, ride.avgSpeedKph!!, 1e-9)
        assertEquals(4, ride.endFuelBars)
        // No economy logged yet: fuel stays null rather than guessed.
        assertNull(ride.fuelUsedLitres)
        assertNull(ride.fuelCost)
    }

    @Test
    fun fuelUsedComesFromTheRidersOwnEconomyAndPrice() {
        val ride = RideMath.finish(
            ride = active(startedAtMs = 0L, startOdometerKm = 12_000),
            endedAtMs = hourMs,
            endOdometerKm = 12_040,
            endFuelBars = 5,
            kmPerLitre = 40.0,
            pricePerLitre = 105.0
        )
        // 40 km at 40 km/L is one litre, priced at the log's average.
        assertEquals(1.0, ride.fuelUsedLitres!!, 1e-9)
        assertEquals(105.0, ride.fuelCost!!, 1e-9)
    }

    @Test
    fun aRideThatNeverLinkedOdometersIsNotInvented() {
        val ride = RideMath.finish(
            ride = active(startedAtMs = 0L, startOdometerKm = null),
            endedAtMs = hourMs,
            endOdometerKm = 8_800,
            endFuelBars = null,
            kmPerLitre = 40.0,
            pricePerLitre = 105.0
        )
        assertNull(ride.distanceKm)
        assertNull(ride.avgSpeedKph)
        assertNull(ride.fuelUsedLitres)
        assertEquals(hourMs, ride.durationMs)
    }

    @Test
    fun distanceIsNeverNegativeWhenTheOdometerReadsBackwards() {
        // A reset odometer must not report a negative distance.
        val ride = RideMath.finish(
            ride = active(startOdometerKm = 8_800),
            endedAtMs = hourMs,
            endOdometerKm = 100,
            endFuelBars = null,
            kmPerLitre = 40.0,
            pricePerLitre = null
        )
        assertNull(ride.distanceKm)
        assertNull(ride.fuelUsedLitres)
    }

    @Test
    fun ridesAreSortedNewestFirstAndTotalsCountOnlyFinishedOnes() {
        val older = RideMath.finish(active(id = 1, startedAtMs = 1_000L, startOdometerKm = 100), 2_000L, 140, null, 40.0, null)
        val newer = RideMath.finish(active(id = 2, startedAtMs = 5_000L, startOdometerKm = 140), 6_000L, 200, null, 40.0, null)
        val running = active(id = 3, startedAtMs = 9_000L, startOdometerKm = 200)

        val sorted = RideMath.newestFirst(listOf(older, running, newer))
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })

        val totals = RideMath.totals(sorted)
        assertEquals(2, totals.rides)               // the running one is not "kept" yet
        assertEquals(100.0, totals.distanceKm, 1e-9) // 40 + 60; the live ride adds nothing
        assertEquals(2.5, totals.fuelLitres, 1e-9)   // 40/40 + 60/40
    }

    @Test
    fun durationsReadTheWayARiderSaysThem() {
        assertEquals("45s", RideMath.formatDuration(45_000L))
        assertEquals("12m", RideMath.formatDuration(12 * 60_000L))
        assertEquals("1h 23m", RideMath.formatDuration(hourMs + 23 * 60_000L))
        assertEquals("2h 05m", RideMath.formatDuration(2 * hourMs + 5 * 60_000L))
        assertEquals("0s", RideMath.formatDuration(0L))
        assertEquals("0s", RideMath.formatDuration(-500L))
    }
}
