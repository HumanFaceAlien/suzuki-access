package com.adithyapaib.sxv

import com.adithyapaib.sxv.nav.GoogleMapsNotificationParser
import com.adithyapaib.sxv.nav.Maneuver
import com.adithyapaib.sxv.nav.MapsNotificationFields
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsNotificationParserTest {

    private val tenAm = LocalTime.of(10, 0)

    private fun parse(fields: MapsNotificationFields) =
        checkNotNull(GoogleMapsNotificationParser.parse(fields, now = tenAm)) { "expected a step from $fields" }

    @Test
    fun distanceInTitleInstructionInText() {
        val step = parse(
            MapsNotificationFields(title = "200 m", text = "Turn left onto MG Road", subText = "12 min · 5.3 km · 18:42")
        )

        assertEquals(Maneuver.LEFT, step.maneuver)
        assertEquals("Turn left onto MG Road", step.instruction)
        assertEquals(200, step.distanceMeters)
        assertEquals(5_300, step.remainingMeters)
        assertEquals(LocalTime.of(18, 42), step.eta)
    }

    @Test
    fun distanceAndInstructionInOneTitle() {
        val step = parse(MapsNotificationFields(title = "In 1.2 km, keep right", subText = "Arrive 8:14 am"))

        assertEquals(Maneuver.KEEP_RIGHT, step.maneuver)
        assertEquals("keep right", step.instruction)
        assertEquals(1_200, step.distanceMeters)
        assertEquals(LocalTime.of(8, 14), step.eta)
    }

    @Test
    fun etaFromRemainingDurationWhenNoClockTime() {
        val step = parse(MapsNotificationFields(title = "Continue on NH 48", subText = "1 hr 5 min · 80 km"))

        assertEquals(Maneuver.STRAIGHT, step.maneuver)
        assertEquals(LocalTime.of(11, 5), step.eta)
        assertEquals(80_000, step.remainingMeters)
    }

    @Test
    fun decimalMilesAreNotReadAsAClockTime() {
        val step = parse(MapsNotificationFields(title = "500 ft", text = "Turn right", subText = "4 min · 1.25 mi"))

        assertEquals(LocalTime.of(10, 4), step.eta)
        assertEquals(2_012, step.remainingMeters)
        assertEquals(152, step.distanceMeters)
    }

    @Test
    fun readsTheOlderCustomLayout() {
        val step = parse(
            MapsNotificationFields(
                views = mapOf(
                    "nav_title" to "300 m",
                    "nav_description" to "Turn right toward Ring Road",
                    "nav_time" to "5 min · 2.1 km · 9:30 PM",
                )
            )
        )

        assertEquals(Maneuver.RIGHT, step.maneuver)
        assertEquals(300, step.distanceMeters)
        assertEquals(2_100, step.remainingMeters)
        assertEquals(LocalTime.of(21, 30), step.eta)
    }

    @Test
    fun arrivalHasZeroDistance() {
        val step = parse(MapsNotificationFields(title = "Arrived", text = "MG Road"))

        assertEquals(Maneuver.ARRIVED, step.maneuver)
        assertEquals(0, step.distanceMeters)
    }

    @Test
    fun reroutingIsFlagged() {
        val step = parse(MapsNotificationFields(title = "Rerouting…"))

        assertEquals(true, step.rerouting)
    }

    @Test
    fun emptyNotificationIsIgnored() {
        assertNull(GoogleMapsNotificationParser.parse(MapsNotificationFields(), now = tenAm))
    }

    @Test
    fun maneuverPhrases() {
        assertEquals(Maneuver.U_TURN_RIGHT, Maneuver.fromInstruction("Make a U-turn"))
        assertEquals(Maneuver.U_TURN_LEFT, Maneuver.fromInstruction("Make a U-turn left"))
        assertEquals(Maneuver.ROUNDABOUT, Maneuver.fromInstruction("At the roundabout, take the 2nd exit"))
        assertEquals(Maneuver.RAMP_LEFT, Maneuver.fromInstruction("Take exit 12 on the left"))
        assertEquals(Maneuver.SLIGHT_RIGHT, Maneuver.fromInstruction("Slight right toward NH 48"))
        assertEquals(Maneuver.SHARP_LEFT, Maneuver.fromInstruction("Sharp left onto Church St"))
        assertEquals(Maneuver.MERGE_LEFT, Maneuver.fromInstruction("Merge left onto the expressway"))
        assertEquals(Maneuver.HEAD_NORTH, Maneuver.fromInstruction("Head north on Brigade Rd"))
        assertEquals(Maneuver.STRAIGHT, Maneuver.fromInstruction("Continue on MG Road"))
    }
}
