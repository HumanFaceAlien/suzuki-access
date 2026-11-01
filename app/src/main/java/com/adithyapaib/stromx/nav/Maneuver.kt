package com.adithyapaib.stromx.nav

import java.util.Locale

/**
 * A turn the cluster can draw, with the byte that goes in nav frame byte 2.
 *
 * [clusterByte] is the official app's Mappls-ID → cluster-byte translation
 * (default branch, which covers every model outside the Access/Burgman TFT
 * family), as decompiled in github.com/pushplekh/redline-suzuki-ride-connect-google-maps
 * `ManeuverMap.kt`. [mapplsId] is the navigation SDK's own ID, kept for logs.
 *
 * None of these glyphs have been checked on a V-STROM SX cluster yet; the
 * Settings tab's test sender is there to do that.
 */
enum class Maneuver(val mapplsId: Int, val clusterByte: Int, val label: String) {
    STRAIGHT(7, 8, "Straight"),
    LEFT(0, 1, "Turn left"),
    SHARP_LEFT(1, 2, "Sharp left"),
    SLIGHT_LEFT(2, 3, "Slight left"),
    RIGHT(3, 4, "Turn right"),
    SHARP_RIGHT(4, 5, "Sharp right"),
    SLIGHT_RIGHT(5, 6, "Slight right"),
    U_TURN_LEFT(6, 7, "U-turn left"),
    U_TURN_RIGHT(41, 39, "U-turn right"),
    KEEP_LEFT(19, 27, "Keep left"),
    KEEP_RIGHT(20, 28, "Keep right"),
    MERGE_LEFT(15, 31, "Merge left"),
    MERGE_RIGHT(16, 32, "Merge right"),
    RAMP_LEFT(74, 44, "Exit left"),
    RAMP_RIGHT(75, 10, "Exit right"),
    ROUNDABOUT(72, 45, "Roundabout"),
    HEAD_NORTH(50, 40, "Head north"),
    HEAD_NORTH_EAST(51, 41, "Head north-east"),
    HEAD_EAST(52, 42, "Head east"),
    HEAD_SOUTH_EAST(53, 15, "Head south-east"),
    HEAD_SOUTH(54, 16, "Head south"),
    HEAD_SOUTH_WEST(55, 17, "Head south-west"),
    HEAD_WEST(56, 18, "Head west"),
    HEAD_NORTH_WEST(57, 19, "Head north-west"),

    /** Sent with status '5', which shows DESTINATION REACHED on the Gixxer. */
    ARRIVED(40, 9, "Arrived");

    companion object {
        /**
         * Best guess from an English Google Maps instruction such as
         * "Turn left onto MG Road" or "In 200 m, keep right". Order matters:
         * the more specific phrases are checked before plain left/right.
         */
        fun fromInstruction(instruction: String?): Maneuver {
            val s = instruction.orEmpty().lowercase(Locale.ROOT)
                .replace(Regex("[‐-—]"), "-")
                .replace(Regex("\\s+"), " ")
            fun has(vararg phrases: String) = phrases.any(s::contains)
            fun word(word: String) = Regex("\\b$word\\b").containsMatchIn(s)

            return when {
                s.startsWith("arrived") ||
                    has("you have arrived", "you've arrived", "arrived at", "destination reached", "your destination") -> ARRIVED
                has("roundabout", "traffic circle", "rotary") -> ROUNDABOUT
                Regex("\\bu[- ]?turn\\b").containsMatchIn(s) -> if (word("left")) U_TURN_LEFT else U_TURN_RIGHT
                (has("exit", "ramp", "slip road")) && word("left") -> RAMP_LEFT
                (has("exit", "ramp", "slip road")) && word("right") -> RAMP_RIGHT
                has("sharp left", "hard left") -> SHARP_LEFT
                has("sharp right", "hard right") -> SHARP_RIGHT
                has("slight left", "slightly left", "bear left", "veer left") -> SLIGHT_LEFT
                has("slight right", "slightly right", "bear right", "veer right") -> SLIGHT_RIGHT
                has("keep left", "stay left", "fork left", "left fork") -> KEEP_LEFT
                has("keep right", "stay right", "fork right", "right fork") -> KEEP_RIGHT
                has("merge") && word("left") -> MERGE_LEFT
                has("merge") && word("right") -> MERGE_RIGHT
                has("turn left", "left onto", "left on ", "left at ") -> LEFT
                has("turn right", "right onto", "right on ", "right at ") -> RIGHT
                has("head north-east", "head northeast") -> HEAD_NORTH_EAST
                has("head north-west", "head northwest") -> HEAD_NORTH_WEST
                has("head south-east", "head southeast") -> HEAD_SOUTH_EAST
                has("head south-west", "head southwest") -> HEAD_SOUTH_WEST
                has("head north") -> HEAD_NORTH
                has("head south") -> HEAD_SOUTH
                has("head east") -> HEAD_EAST
                has("head west") -> HEAD_WEST
                else -> STRAIGHT
            }
        }
    }
}
