package com.adithyapaib.sxv.garage

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Everything the garage keeps, persisted as one small JSON file in app-private storage. */
data class GarageData(
    val fuel: List<FuelRecord> = emptyList(),
    val items: List<MaintenanceItem> = emptyList(),
    /** Last odometer the bike reported, so countdowns work while the bike is off. */
    val lastOdometerKm: Int? = null,
    /** Reminders already notified, keyed by item, cycle and state, so each fires once. */
    val notified: Set<String> = emptySet(),
    val nextId: Long = 1
)

class GarageStore(dir: File) {
    private val file = File(dir, "garage.json")

    fun load(): GarageData = try {
        if (!file.exists()) GarageData() else decode(JSONObject(file.readText()))
    } catch (_: Exception) {
        // A corrupt file must not take the app down; keep it aside and start clean.
        file.renameTo(File(file.parentFile, "garage.corrupt.json"))
        GarageData()
    }

    fun save(data: GarageData) {
        val tmp = File(file.parentFile, "garage.json.tmp")
        tmp.writeText(encode(data).toString())
        tmp.renameTo(file)
    }

    private fun encode(data: GarageData) = JSONObject().apply {
        put("version", 1)
        put("nextId", data.nextId)
        data.lastOdometerKm?.let { put("lastOdometerKm", it) }
        put("notified", JSONArray(data.notified.toList()))
        put("fuel", JSONArray().apply {
            data.fuel.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("atMs", r.atMs); put("odometerKm", r.odometerKm)
                    put("litres", r.litres); put("cost", r.cost); put("fullTank", r.fullTank)
                })
            }
        })
        put("items", JSONArray().apply {
            data.items.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id); put("name", i.name)
                    i.intervalKm?.let { put("intervalKm", it) }
                    i.intervalDays?.let { put("intervalDays", it) }
                    i.lastDoneKm?.let { put("lastDoneKm", it) }
                    i.lastDoneAtMs?.let { put("lastDoneAtMs", it) }
                })
            }
        })
    }

    private fun decode(json: JSONObject): GarageData {
        val fuel = json.optJSONArray("fuel") ?: JSONArray()
        val items = json.optJSONArray("items") ?: JSONArray()
        val notified = json.optJSONArray("notified") ?: JSONArray()
        return GarageData(
            fuel = (0 until fuel.length()).map { index ->
                val o = fuel.getJSONObject(index)
                FuelRecord(
                    id = o.getLong("id"),
                    atMs = o.getLong("atMs"),
                    odometerKm = o.getInt("odometerKm"),
                    litres = o.getDouble("litres"),
                    cost = o.getDouble("cost"),
                    fullTank = o.optBoolean("fullTank", true)
                )
            },
            items = (0 until items.length()).map { index ->
                val o = items.getJSONObject(index)
                MaintenanceItem(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    intervalKm = o.optIntOrNull("intervalKm"),
                    intervalDays = o.optIntOrNull("intervalDays"),
                    lastDoneKm = o.optIntOrNull("lastDoneKm"),
                    lastDoneAtMs = if (o.has("lastDoneAtMs")) o.getLong("lastDoneAtMs") else null
                )
            },
            lastOdometerKm = json.optIntOrNull("lastOdometerKm"),
            notified = (0 until notified.length()).map { notified.getString(it) }.toSet(),
            nextId = json.optLong("nextId", 1)
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) getInt(key) else null
}
