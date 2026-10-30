package com.adithyapaib.sxv.nav

import java.time.Duration
import java.time.LocalTime
import java.util.Locale

/** Text pulled from one Google Maps navigation notification. */
data class MapsNotificationFields(
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    /** Text of the custom layout's views by resource name (`nav_title`, `nav_time`, …), older Maps only. */
    val views: Map<String, String> = emptyMap(),
)

/** One navigation step, ready to be encoded for the cluster. */
data class NavInstruction(
    val maneuver: Maneuver,
    val instruction: String,
    val distanceMeters: Int?,
    val remainingMeters: Int?,
    val eta: LocalTime?,
    val rerouting: Boolean = false,
)

/**
 * Reads Google Maps' ongoing navigation notification.
 *
 * Current Maps puts the instruction and the distance to it in the title/text
 * ("200 m" / "Turn left onto MG Road", or "In 200 m, turn left") and the trip
 * line in the sub-text ("12 min · 5.3 km · 18:42", "Arrive 8:14 am"). Older
 * versions used a custom layout (`nav_title`, `nav_description`, `nav_time`),
 * which [MapsNotificationFields.views] covers. Only English instructions map to
 * specific arrows; anything else shows as straight.
 */
object GoogleMapsNotificationParser {

    private const val UNIT = "(km|m|mi|ft|yd)"
    private val leadingDistance = Regex("""^(?:in\s+)?(\d+(?:[.,]\d+)?)\s*$UNIT\b""", RegexOption.IGNORE_CASE)
    private val anyDistance = Regex("""(\d+(?:[.,]\d+)?)\s*$UNIT\b""", RegexOption.IGNORE_CASE)
    /** "18:42", "8:14 am", "18.42"; not a decimal distance such as "1.25 mi". */
    private val clock = Regex(
        """(?<![\d.,])(\d{1,2})[:.](\d{2})(?!\d)(?!\s*$UNIT\b)(?:\s*([ap])\.?\s*m\b\.?)?""",
        RegexOption.IGNORE_CASE,
    )
    private val hours = Regex("""(\d+)\s*(?:h|hr|hrs|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)
    private val leadingSeparators = Regex("""^[\s,·:\-–—]+""")

    fun parse(fields: MapsNotificationFields, now: LocalTime = LocalTime.now()): NavInstruction? {
        val titleLike = listOfNotNull(fields.views["nav_title"], fields.title, fields.views["lockscreen_directions"])
            .clean()
        val instructionLike = listOfNotNull(
            fields.views["nav_description"], fields.text, fields.bigText, fields.views["lockscreen_oneliner"],
        ).clean()
        val tripLine = listOfNotNull(
            fields.views["nav_time"], fields.views["header_text"], fields.subText, fields.views["lockscreen_eta"],
        ).clean().joinToString(" · ")

        val candidates = titleLike + instructionLike
        if (candidates.isEmpty() && tripLine.isEmpty()) return null

        val rerouting = candidates.any { it.contains("rerouting", true) || it.contains("recalculating", true) }

        val distance = candidates.firstNotNullOfOrNull { leadingDistance.find(it)?.toMeters() }
            ?: titleLike.firstNotNullOfOrNull { anyDistance.find(it)?.toMeters() }

        val stripped = candidates.map { it.replaceFirst(leadingDistance, "").replaceFirst(leadingSeparators, "").trim() }
            .filter { it.isNotEmpty() && anyDistance.matchEntire(it) == null }
        val instruction = stripped.firstOrNull { Maneuver.fromInstruction(it) != Maneuver.STRAIGHT }
            ?: stripped.firstOrNull()
            .orEmpty()

        if (instruction.isEmpty() && distance == null && !rerouting) return null

        val maneuver = Maneuver.fromInstruction(instruction)
        return NavInstruction(
            maneuver = maneuver,
            instruction = instruction,
            distanceMeters = if (maneuver == Maneuver.ARRIVED) 0 else distance,
            remainingMeters = anyDistance.find(tripLine)?.toMeters(),
            eta = clockTime(tripLine) ?: duration(tripLine)?.let { now.plus(it).withSecond(0).withNano(0) },
            rerouting = rerouting,
        )
    }

    private fun List<String>.clean(): List<String> = map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }

    private fun MatchResult.toMeters(): Int? {
        val value = groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val metres = when (groupValues[2].lowercase(Locale.ROOT)) {
            "km" -> value * 1000
            "mi" -> value * 1609.344
            "ft" -> value * 0.3048
            "yd" -> value * 0.9144
            else -> value
        }
        return Math.round(metres).toInt()
    }

    private fun clockTime(text: String): LocalTime? {
        for (match in clock.findAll(text)) {
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            when (match.groupValues[3].lowercase(Locale.ROOT)) {
                "a" -> if (hour == 12) hour = 0
                "p" -> if (hour < 12) hour += 12
            }
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }
        return null
    }

    private fun duration(text: String): Duration? {
        val h = hours.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val m = minutes.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return if (h == 0L && m == 0L) null else Duration.ofHours(h).plusMinutes(m)
    }
}
