package com.adithyapaib.sxv.ble

/**
 * Builders for the **phone -> cluster** command frames.
 *
 * Every frame is 30 bytes:
 *
 * ```
 * [0]     0xA5            header
 * [1]     command
 * [28]    checksum        see [SuzukiChecksum] (bytes 1..27)
 * [29]    0x7F            terminator
 * ```
 *
 * Every builder returns `null` rather than a malformed frame when the
 * assembled template does not land on exactly 30 bytes, because the APK's own
 * string templates are length-sensitive.
 *
 * Sources: `CallReceiverBroadcast:144-165` (command '4'),
 * `A0.java:483-517` (navigation), confirmed by the `"?4"` and `"?110"`
 * literals in the APK's `classes.dex` string pool.
 */
object SuzukiCommandPacket {

    const val PACKET_SIZE = 30

    const val HEADER_INDEX = 0
    const val COMMAND_INDEX = 1
    const val CHECKSUM_INDEX = 28
    const val TERMINATOR_INDEX = 29

    const val HEADER_BYTE: Byte = 0xA5.toByte()
    const val TERMINATOR_BYTE: Byte = 0x7F.toByte()

    /** Missed-call / notification text. */
    const val COMMAND_NOTIFICATION_TEXT: Byte = '4'.code.toByte()

    /** Turn-by-turn navigation (the official app's `a531`). */
    const val COMMAND_NAVIGATION: Byte = '1'.code.toByte()

    /** Maneuver byte the official app sends when there is nothing to show. */
    const val NO_MANEUVER: Int = 0x2E

    /**
     * Rider identity. The official app writes this right after enabling FFF2
     * notifications; the cluster does not stream telemetry until it arrives.
     */
    const val COMMAND_IDENTITY: Byte = '6'.code.toByte()

    /** Display-name width for [buildIdentityPacket]. */
    const val MAX_IDENTITY_NAME_LENGTH = 20

    /**
     * Visible text width for [buildNotificationTextPacket].
     *
     * The APK pads its text field to **23** chars at bytes[2..24], then overwrites
     * byte[2] with `0xFF`, byte[3] with the count and byte[24] with `'N'`,
     * leaving bytes[4..23] visible.
     */
    const val MAX_VISIBLE_TEXT_LENGTH = 20

    /**
     * Missed-call notification frame.
     *
     * ```
     * [1]   '4'
     * [2]   0xFF
     * [3]   missedCallCount
     * [4..23] text, [MAX_VISIBLE_TEXT_LENGTH] ASCII chars, NUL padded
     * [24]  'N'
     * [25..27] 0xFF
     * ```
     *
     * @param missedCallCount value the APK stores at byte[3].
     */
    fun buildNotificationTextPacket(
        text: String?,
        missedCallCount: Byte = 0,
        mode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): ByteArray {
        val sanitized = text.orEmpty().sanitizeVisible()
            .take(MAX_VISIBLE_TEXT_LENGTH)
            .padTo(MAX_VISIBLE_TEXT_LENGTH)

        val packet = ByteArray(PACKET_SIZE)
        packet[HEADER_INDEX] = HEADER_BYTE
        packet[COMMAND_INDEX] = COMMAND_NOTIFICATION_TEXT
        packet[2] = 0xFF.toByte()
        packet[3] = missedCallCount
        sanitized.toByteArray(Charsets.US_ASCII).copyInto(packet, destinationOffset = 4)
        packet[24] = 'N'.code.toByte()
        for (index in 25..27) packet[index] = 0xFF.toByte()
        packet[CHECKSUM_INDEX] = SuzukiChecksum.compute(packet, mode)
        packet[TERMINATOR_INDEX] = TERMINATOR_BYTE
        return packet
    }

    /**
     * Identity frame (the official app's `a536`), the handshake that starts the
     * telemetry stream.
     *
     * ```
     * [1]      '6'
     * [2..21]  rider name, [MAX_IDENTITY_NAME_LENGTH] ASCII chars, NUL padded
     * [22..26] 0xFF
     * [27]     'F' first connection to this cluster, 'R' reconnect
     * ```
     *
     * Layout from github.com/mrwick1/suzuki-connect-re `tools/protocol.py`
     * (Gixxer SF 150, replay-tested there); not yet confirmed on the V-STROM SX.
     */
    fun buildIdentityPacket(
        name: String,
        reconnect: Boolean = true,
        mode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): ByteArray {
        val sanitized = name.sanitizeVisible()
            .take(MAX_IDENTITY_NAME_LENGTH)
            .padTo(MAX_IDENTITY_NAME_LENGTH)

        val packet = ByteArray(PACKET_SIZE)
        packet[HEADER_INDEX] = HEADER_BYTE
        packet[COMMAND_INDEX] = COMMAND_IDENTITY
        sanitized.toByteArray(Charsets.US_ASCII).copyInto(packet, destinationOffset = 2)
        for (index in 22..26) packet[index] = 0xFF.toByte()
        packet[27] = (if (reconnect) 'R' else 'F').code.toByte()
        packet[CHECKSUM_INDEX] = SuzukiChecksum.compute(packet, mode)
        packet[TERMINATOR_INDEX] = TERMINATOR_BYTE
        return packet
    }

    /**
     * Turn-by-turn navigation frame.
     *
     * ```
     * [1]      '1'
     * [2]      maneuver byte (cluster glyph, see nav/Maneuver.kt)
     * [3]      0xFF
     * [4..7]   distance to next maneuver, 4 ASCII chars ("0450", "01.2")
     * [8]      its unit, 'M' or 'K'
     * [9..14]  ETA, 6 ASCII chars: "00HHMM" (24 h) or "HHMMAM"/"HHMMPM" (12 h)
     * [15..17] 0xFF
     * [18..21] distance left on the route, 4 ASCII chars
     * [22]     its unit, 'M' or 'K'
     * [23]     status: '1' normal, '2' re-routing, '5' destination reached,
     *          '0' exit; '0'/'2'/'4'/'6' make the cluster ignore byte 2
     * [24]     '1' keep navigating, '0' end navigation
     * [25..27] 0xFF
     * ```
     *
     * Layout from github.com/pushplekh/redline-suzuki-ride-connect-google-maps
     * `NOTES.md` (Gixxer/Access, status digits photo-verified there); not yet
     * confirmed on the V-STROM SX. Text fields are written as given, so callers
     * use [formatDistance] and [formatEta].
     */
    fun buildNavigationPacket(
        maneuver: Int,
        distanceToManeuver: String,
        distanceToManeuverUnit: Char,
        eta: String,
        remainingDistance: String,
        remainingDistanceUnit: Char,
        status: Char = '1',
        continueNavigation: Boolean = true,
        mode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): ByteArray {
        val packet = ByteArray(PACKET_SIZE)
        packet[HEADER_INDEX] = HEADER_BYTE
        packet[COMMAND_INDEX] = COMMAND_NAVIGATION
        packet[2] = (maneuver and 0xFF).toByte()
        packet[3] = 0xFF.toByte()
        packet.writeField(4, 4, distanceToManeuver)
        packet[8] = distanceToManeuverUnit.code.toByte()
        packet.writeField(9, 6, eta)
        for (index in 15..17) packet[index] = 0xFF.toByte()
        packet.writeField(18, 4, remainingDistance)
        packet[22] = remainingDistanceUnit.code.toByte()
        packet[23] = status.code.toByte()
        packet[24] = (if (continueNavigation) '1' else '0').code.toByte()
        for (index in 25..27) packet[index] = 0xFF.toByte()
        packet[CHECKSUM_INDEX] = SuzukiChecksum.compute(packet, mode)
        packet[TERMINATOR_INDEX] = TERMINATOR_BYTE
        return packet
    }

    /** Frame that tells the cluster navigation has ended. */
    fun buildNavigationEndPacket(mode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM): ByteArray =
        buildNavigationPacket(
            maneuver = NO_MANEUVER,
            distanceToManeuver = "",
            distanceToManeuverUnit = 'M',
            eta = "",
            remainingDistance = "",
            remainingDistanceUnit = 'M',
            status = '0',
            continueNavigation = false,
            mode = mode,
        )

    /**
     * Distance as the cluster's 4-char field plus unit: under 1 km whole metres
     * (`"0450" M`), under 100 km one decimal (`"01.2" K`), then whole km up to
     * 9999. Unknown or negative distances are blank.
     */
    fun formatDistance(meters: Int?): Pair<String, Char> = when {
        meters == null || meters < 0 -> "    " to 'M'
        meters < 1_000 -> "%04d".format(java.util.Locale.ROOT, meters) to 'M'
        meters < 99_950 -> "%04.1f".format(java.util.Locale.ROOT, meters / 1000.0) to 'K'
        else -> "%04d".format(java.util.Locale.ROOT, minOf(9_999, Math.round(meters / 1000.0).toInt())) to 'K'
    }

    /** ETA as the cluster's 6-char field; blank when unknown. */
    fun formatEta(time: java.time.LocalTime?, twelveHour: Boolean): String {
        if (time == null) return "      "
        if (!twelveHour) return "00%02d%02d".format(java.util.Locale.ROOT, time.hour, time.minute)
        val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
        return "%02d%02d%s".format(java.util.Locale.ROOT, hour12, time.minute, if (time.hour < 12) "AM" else "PM")
    }

    private fun ByteArray.writeField(offset: Int, width: Int, value: String) {
        val text = value.sanitizeVisible().take(width).padStart(width, ' ')
        text.toByteArray(Charsets.US_ASCII).copyInto(this, destinationOffset = offset)
    }

    private fun String.sanitizeVisible(): String = buildString(length) {
        this@sanitizeVisible.forEach { char ->
            append(if (char.code in 32..126) char else ' ')
        }
    }

    private fun String.padTo(length: Int): String =
        if (this.length >= length) this else this + "\u0000".repeat(length - this.length)
}
