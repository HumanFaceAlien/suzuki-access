package com.adithyapaib.sxv

import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.VStromTelemetry
import com.adithyapaib.sxv.ride.RoadbookEvent
import com.adithyapaib.sxv.ride.RoadbookRecorder
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadbookRecorderTest {

    private fun frame(speed: Int, tripA: Double, temp: Int? = 60, fuel: Int = 5) = VStromTelemetry(
        odometer = 12334, tripA = tripA, tripB = 135.9, fuelBars = fuel,
        fuelLiters = fuel * 2.0, isFuelValid = true, speed = speed, engineTempC = temp
    )

    private fun RoadbookRecorder.events() = entries.map { it.event }

    @Test
    fun firstFrameWritesStartOnly() {
        val recorder = RoadbookRecorder { 0L }
        recorder.onTelemetry(frame(speed = 16, tripA = 395.0))
        assertEquals(listOf(RoadbookEvent.START), recorder.events())
    }

    @Test
    fun warmUpIsLoggedOnceWhenTemperatureCrossesThreshold() {
        val recorder = RoadbookRecorder { 0L }
        recorder.onTelemetry(frame(speed = 20, tripA = 395.0, temp = 67))
        recorder.onTelemetry(frame(speed = 20, tripA = 395.1, temp = 80))
        recorder.onTelemetry(frame(speed = 20, tripA = 395.2, temp = 88))
        assertEquals(listOf(RoadbookEvent.WARMED_UP, RoadbookEvent.START), recorder.events())
        assertEquals(80, recorder.entries.first().engineTempC)
    }

    @Test
    fun aSingleZeroFrameIsNotAStop() {
        val recorder = RoadbookRecorder { 0L }
        recorder.onTelemetry(frame(speed = 20, tripA = 395.0))
        recorder.onTelemetry(frame(speed = 0, tripA = 395.0))
        recorder.onTelemetry(frame(speed = 18, tripA = 395.1))
        assertEquals(listOf(RoadbookEvent.START), recorder.events())
    }

    @Test
    fun stopThenRideAgainIsLoggedAtTheRightDistance() {
        val recorder = RoadbookRecorder { 0L }
        recorder.onTelemetry(frame(speed = 20, tripA = 397.3))
        recorder.onTelemetry(frame(speed = 0, tripA = 397.4))
        recorder.onTelemetry(frame(speed = 0, tripA = 397.4))
        recorder.onTelemetry(frame(speed = 12, tripA = 397.4))
        assertEquals(listOf(RoadbookEvent.MOVING, RoadbookEvent.STOPPED, RoadbookEvent.START), recorder.events())
        assertEquals(397.4, recorder.entries[1].tripKm ?: 0.0, 0.0)
    }

    @Test
    fun fuelDropAndLinkChangesAndMessages() {
        val recorder = RoadbookRecorder { 0L }
        recorder.onConnection(BleConnectionState.CONNECTED)
        recorder.onTelemetry(frame(speed = 20, tripA = 395.0, fuel = 5))
        recorder.onTelemetry(frame(speed = 20, tripA = 395.5, fuel = 4))
        recorder.onMessageSent("FUEL STOP")
        recorder.onConnection(BleConnectionState.DISCONNECTED)
        assertEquals(
            listOf(
                RoadbookEvent.LINK_LOST,
                RoadbookEvent.MESSAGE_SENT,
                RoadbookEvent.FUEL_DROP,
                RoadbookEvent.START,
                RoadbookEvent.CONNECTED
            ),
            recorder.events()
        )
        assertEquals(4, recorder.entries[2].fuelBars)
        assertEquals("FUEL STOP", recorder.entries[1].message)
    }
}
