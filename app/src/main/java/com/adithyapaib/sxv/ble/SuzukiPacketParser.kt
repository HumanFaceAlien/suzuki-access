package com.adithyapaib.sxv.ble

/**
 * Decodes the **cluster -> phone** 30-byte notification frame.
 *
 * Command `0x37` ('7') is sent *by* the cluster. The APK never builds one —
 * there is no `"?7"` literal anywhere in `classes.dex` — so it is strictly
 * inbound.
 *
 * The field offsets below were cross-checked against five independent call
 * sites in the APK that parse the same frame:
 * `C.java:817`, `NavigationActivity:190`, `RouteActivity:284`,
 * `RiderProfileActivity:147`, `CreateProfileActivity:396`.
 *
 * ```
 * [0]        0xA5
 * [1]        '7'
 * [2..4]     speed in km/h, three digits
 * [5..10]    odometer, 6 digits
 * [11..16]   trip A, 6 digits (tenths)
 * [17..22]   trip B, 6 digits (tenths)
 * [23]       gear position — always 'N' in every capture so far
 * [24]       fuel bars, ASCII '1'..'6'
 * [25..27]   cumulative fuel-consumption accumulator (see note below)
 * [28]       checksum, see [SuzukiChecksum]
 * [29]       0x7F
 * ```
 *
 * Bytes [25..27] are read but discarded by the APK's dashboard parser
 * (`C.java:831-832`); `NavigationActivity:203-219` decodes them as a 24-bit
 * fixed-point value into a field that is never read. They are covered by the
 * checksum but not surfaced here.
 *
 * The APK rejects a frame unless the header, command, terminator **and**
 * checksum all match; this parser mirrors that gate so a corrupted frame
 * never surfaces as plausible telemetry.
 */
object SuzukiPacketParser {

    private const val PACKET_SIZE = 30

    const val HEADER_INDEX = 0
    const val COMMAND_INDEX = 1
    const val CHECKSUM_INDEX = 28
    const val TERMINATOR_INDEX = 29

    const val HEADER_BYTE: Byte = 0xA5.toByte()
    const val COMMAND_BYTE: Byte = '7'.code.toByte()
    const val TERMINATOR_BYTE: Byte = 0x7F.toByte()

    /** Bytes[1..24] as ASCII. Index `i` here is absolute byte `i + 1`. */
    private const val ASCII_START = 1
    private const val ASCII_END_EXCLUSIVE = 25

    private val ODOMETER_REGEX = Regex("\\d{6}")
    private val TRIP_REGEX = Regex("\\d{6}")
    private val SPEED_REGEX = Regex("\\d{3}")

    fun parse(
        bytes: ByteArray,
        checksumMode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): VStromTelemetry? {
        if (bytes.size != PACKET_SIZE) return null
        if (bytes[HEADER_INDEX] != HEADER_BYTE) return null
        if (bytes[COMMAND_INDEX] != COMMAND_BYTE) return null
        if (bytes[TERMINATOR_INDEX] != TERMINATOR_BYTE) return null
        if (bytes[CHECKSUM_INDEX] != SuzukiChecksum.compute(bytes, checksumMode)) return null

        return try {
            val ascii = bytes.copyOfRange(ASCII_START, ASCII_END_EXCLUSIVE)
                .toString(Charsets.US_ASCII)

            val speedString = ascii.substring(1, 4)         // bytes[2..4]
            val odometerString = ascii.substring(4, 10)     // bytes[5..10]
            val tripAString = ascii.substring(10, 16)  // bytes[11..16]
            val tripBString = ascii.substring(16, 22) // bytes[17..22]
            val fuelIndicator = ascii[23]           // bytes[24]

            if (!ODOMETER_REGEX.matches(odometerString) ||
                !TRIP_REGEX.matches(tripAString) ||
                !TRIP_REGEX.matches(tripBString)
            ) {
                return null
            }

            val (fuelBars, fuelLiters, isFuelValid) = if (fuelIndicator in '1'..'6') {
                val bars = fuelIndicator.digitToInt()
                Triple(bars, bars * 2.0, true)
            } else {
                Triple(0, -1.0, false)
            }

            VStromTelemetry(
                odometer = odometerString.trimStart('0').ifEmpty { "0" }.toInt(),
                tripA = tripAString.toInt() / 10.0,
                tripB = tripBString.toInt() / 10.0,
                fuelBars = fuelBars,
                fuelLiters = fuelLiters,
                isFuelValid = isFuelValid,
                speed = if (SPEED_REGEX.matches(speedString)) {
                    speedString.toInt()
                } else {
                    0
                },
            )
        } catch (_: RuntimeException) {
            null
        }
    }
}
