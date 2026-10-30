package com.adithyapaib.sxv

import com.adithyapaib.sxv.garage.BarDrop
import com.adithyapaib.sxv.garage.EstimateBasis
import com.adithyapaib.sxv.garage.FuelEstimator
import com.adithyapaib.sxv.garage.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimatorTest {

    private fun full(id: Long, odo: Int, litres: Double) = FuelRecord(id, id * 1000, odo, litres, litres * 105, true)

    /** Two closed tanks at exactly 40 km/L, last brim fill at 12,400 km. */
    private val history = listOf(
        full(1, 12_000, 10.0),
        full(2, 12_400, 10.0),
        full(3, 12_800, 10.0)
    )

    @Test
    fun barsOnlyBeforeAnyEconomyIsTheMiddleOfTheBar() {
        val estimate = FuelEstimator.estimate(emptyList(), emptyList(), odometerKm = 5_000, currentBars = 3, tankSetting = 12.0)
        assertNotNull(estimate)
        estimate!!
        assertEquals(EstimateBasis.BarsOnly, estimate.basis)
        // Nominal bar 3 spans 4..6 L on a 12 L tank.
        assertEquals(5.0, estimate.litres, 1e-9)
        assertNull(estimate.rangeKm)
    }

    @Test
    fun countsDownFromTheLastFullTank() {
        val estimate = FuelEstimator.estimate(history, emptyList(), odometerKm = 12_960, currentBars = null, tankSetting = 12.0)!!
        assertEquals(EstimateBasis.FullTank, estimate.basis)
        assertEquals(40.0, estimate.kmPerLitre ?: 0.0, 1e-9)
        // 160 km at 40 km/L = 4 L burned from 12 L.
        assertEquals(8.0, estimate.litres, 1e-9)
        assertEquals(320, estimate.rangeKm)
        assertEquals(160, estimate.kmSinceAnchor)
    }

    @Test
    fun partialTopUpsAreAddedBack() {
        val withTopUp = history + FuelRecord(4, 4_000, 12_900, 2.0, 210.0, false)
        val estimate = FuelEstimator.estimate(withTopUp, emptyList(), odometerKm = 12_960, currentBars = null, tankSetting = 12.0)!!
        // The top-up joins an open tank, so the economy is unchanged at 40 km/L.
        assertEquals(10.0, estimate.litres, 1e-9)
    }

    @Test
    fun learnedBarRecentresTheEstimate() {
        // In both earlier tanks the gauge stepped to 3 bars at 240 km = 6 L used -> 6 L left.
        val drops = listOf(
            BarDrop(12_240, 3, 0),
            BarDrop(12_640, 3, 0),
            // This tank: it stepped to 3 later than the countdown predicts (we rode gently).
            BarDrop(12_880, 3, 0)
        )
        val estimate = FuelEstimator.estimate(history, drops, odometerKm = 12_920, currentBars = 3, tankSetting = 12.0)!!
        assertEquals(EstimateBasis.BarCheckpoint, estimate.basis)
        assertEquals(6.0, estimate.learnedBarLevels[2] ?: 0.0, 1e-9)
        // 6 L at the step, minus 40 km at 40 km/L.
        assertEquals(5.0, estimate.litres, 1e-9)
        assertEquals(3, estimate.anchorBars)
    }

    @Test
    fun aBiggerBrimFillThanTheSettingRaisesCapacity() {
        val records = listOf(full(1, 10_000, 13.1))
        assertEquals(13.1, FuelEstimator.capacity(records, 12.0), 1e-9)
    }

    @Test
    fun uncertaintyGrowsWithDistanceSinceTheAnchor() {
        val near = FuelEstimator.estimate(history, emptyList(), 12_820, null, 12.0)!!
        val far = FuelEstimator.estimate(history, emptyList(), 13_100, null, 12.0)!!
        assertTrue(far.uncertaintyLitres > near.uncertaintyLitres)
    }

    @Test
    fun historyRunsFromTheFillToNow() {
        val drops = listOf(BarDrop(12_880, 3, 0))
        val estimate = FuelEstimator.estimate(history, drops, 12_920, 3, 12.0)!!
        assertEquals(listOf(12_800, 12_880, 12_920), estimate.history.map { it.odometerKm })
        assertEquals(12.0, estimate.history.first().litres, 1e-9)
    }
}
