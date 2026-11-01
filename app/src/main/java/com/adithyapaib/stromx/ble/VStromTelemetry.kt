package com.adithyapaib.stromx.ble

/** A decoded snapshot of the values exposed by the V-Strom instrument cluster. */
data class VStromTelemetry(
    /** Odometer in km, read from bytes[5..10] as six digits with leading zeros stripped. */
    val odometer: Int,
    /** Trip A in km, read from bytes[11..16] (six digits, tenths). */
    val tripA: Double,
    /** Trip B in km, read from bytes[17..22] (six digits, tenths). */
    val tripB: Double,
    /** Fuel gauge bars 1..6; 0 when the byte is not a valid bar count. */
    val fuelBars: Int,
    /** TODO(capacity): assumed 2 L per bar. No calibration data was found in the APK. */
    val fuelLiters: Double,
    val isFuelValid: Boolean,
    /**
     * Current speed in km/h, read from bytes[2..4] as three digits.
     *
     * Confirmed: the APK parses it with `Integer.parseInt(substring(2, 5))`
     * (`NavigationActivity.java:195`, `C.java:827`) and writes it to the
     * `top_speed` SharedPreferences key (`j.java:44-46`); `NavigationActivity`
     * then keeps a running max (`this.X > this.U`). Real captures show it
     * tracking the ride: 0 while parked, ramping through 8/12/15/21 and
     * peaking at 33 km/h.
     */
    val speed: Int = 0,
    /**
     * Engine temperature in °C, read raw from byte[25]. Likely oil or engine temperature rather
     * than coolant (the V-Strom SX 250 is oil-cooled), but not yet checked against the cluster's
     * own reading. Null when the byte is outside a plausible range.
     *
     * Evidence: across 218 captured frames it rises from 27 (cold, parked) to 90 while riding,
     * dips when stopped, and falls during a break.
     */
    val engineTempC: Int? = null,
)
