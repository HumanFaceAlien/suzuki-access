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

    /** Missed-call / notification text. Sibling of [SuzukiDisplaySender]'s '5', one char narrower. */
    const val COMMAND_NOTIFICATION_TEXT: Byte = '4'.code.toByte()

    /**
     * Turn-by-turn navigation. Only byte[1] holds the command ('1'); the APK's
     * `"?110"` is a *template* whose bytes[2..3] are overwritten with the
     * maneuver code and `0xFF`.
     */
    const val COMMAND_NAVIGATION: Byte = '1'.code.toByte()

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
     * Field widths are taken from the APK's `"?110"` template. Only
     * `distanceToManeuver` and `remainingDistance` are evidenced by their
     * defaults (`"0000"` at `A0.java:485-486`); the widths of `fieldM`,
     * `fieldN` and `fieldO` were **not** established, because the Mappls
     * navigation callbacks that fill them did not decompile. They are passed
     * through verbatim and the builder returns `null` if the result is not
     * exactly 30 bytes, so a wrong assumption fails loudly instead of
     * transmitting a malformed frame.
     *
     * ```
     * template = "?110" + distanceToManeuver + fieldM + fieldN + "000" +
     *            remainingDistance + fieldO + gpsStatus + gpsStatus2 + "00000"
     * [0]      0xA5         [2]      maneuverCode     [3] 0xFF
     * [9]      0x20         (only on TFT editions, when fieldN starts with '0')
     * [15..17] 0xFF         [25..27] 0xFF
     * ```
     */
    fun buildNavigationPacket(
        maneuverCode: Byte,
        gpsStatus: Char,
        gpsStatus2: Char,
        distanceToManeuver: String = "0000",
        fieldM: String = "000",
        fieldN: String = "000",
        remainingDistance: String = "0000",
        fieldO: String = "00",
        isTftEdition: Boolean = false,
        mode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): ByteArray? {
        val template = "?110" + distanceToManeuver + fieldM + fieldN + "000" +
            remainingDistance + fieldO + gpsStatus + gpsStatus2 + "00000"

        if (template.length != PACKET_SIZE) return null

        val packet = template.toByteArray(Charsets.US_ASCII).clone()
        packet[HEADER_INDEX] = HEADER_BYTE
        packet[2] = maneuverCode
        packet[3] = 0xFF.toByte()
        if (isTftEdition && fieldN.firstOrNull() == '0') {
            packet[9] = 0x20
        }
        for (index in 15..17) packet[index] = 0xFF.toByte()
        for (index in 25..27) packet[index] = 0xFF.toByte()
        packet[CHECKSUM_INDEX] = SuzukiChecksum.compute(packet, mode)
        packet[TERMINATOR_INDEX] = TERMINATOR_BYTE
        return packet
    }

    private fun String.sanitizeVisible(): String = buildString(length) {
        this@sanitizeVisible.forEach { char ->
            append(if (char.code in 32..126) char else ' ')
        }
    }

    private fun String.padTo(length: Int): String =
        if (this.length >= length) this else this + "\u0000".repeat(length - this.length)
}
