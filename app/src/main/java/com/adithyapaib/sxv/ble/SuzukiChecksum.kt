package com.adithyapaib.sxv.ble

/**
 * Frame checksum for the 30-byte Suzuki cluster protocol.
 *
 * Verified against **two independent implementations** inside the official APK
 * (`suzuki.com.suzuki` v7.5, versionCode 233, Play-signed):
 *
 *  - `SuzukiApplication.a()` — the shared implementation
 *  - `C0940y.java:207-211` — a separate inline copy used for the status packet
 *
 * Both sum **bytes 1..27** (not 0..27) and reduce mod 256, so the `0xA5` header
 * is deliberately excluded from the sum.
 *
 * The result is model-dependent (`K.g`, assigned from the BLE device name in
 * `services/d.java:110-155`):
 *
 * ```
 * ?AS0x / ?AS0x   Access 125        -> one's complement
 * ?AS1x / ?AS1x   Burgman Street    -> one's complement
 * ?BS2x           Avenis            -> plain sum
 * ?BS3x           Burgman Street EX -> plain sum
 * ?BM01           V-STROM SX        -> plain sum   <- this app's target
 * ?BM1x           Gixxer / Gixxer SF-> plain sum
 * ?BM2x           Gixxer 250 / SF   -> plain sum
 * ```
 */
object SuzukiChecksum {

    /** First and last byte folded into the sum. Byte 0 (`0xA5`) is excluded. */
    private const val FIRST_SUMMED_INDEX = 1
    private const val LAST_SUMMED_INDEX = 27

    private const val REDUCTION_MASK = 0xFF

    /**
     * [PLAIN_SUM] for V-Strom/motorcycle and the `BS` scooter families;
     * [ONES_COMPLEMENT] for the `AS` scooters (Access 125, Burgman Street).
     */
    enum class Mode { PLAIN_SUM, ONES_COMPLEMENT }

    fun compute(packet: ByteArray, mode: Mode = Mode.PLAIN_SUM): Byte {
        require(packet.size > LAST_SUMMED_INDEX) {
            "checksum needs at least ${LAST_SUMMED_INDEX + 1} bytes, got ${packet.size}"
        }
        var sum = 0
        for (index in FIRST_SUMMED_INDEX..LAST_SUMMED_INDEX) {
            sum += packet[index].toInt() and REDUCTION_MASK
        }
        val reduced = sum and REDUCTION_MASK
        return when (mode) {
            Mode.PLAIN_SUM -> reduced.toByte()
            Mode.ONES_COMPLEMENT -> (255 - reduced).toByte()
        }
    }
}
