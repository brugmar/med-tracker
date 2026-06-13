package com.medtracker.app.data

import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_FORMAT = "com.medtracker.app.backup"
private const val BACKUP_VERSION = 1

data class BackupData(
    val medicines: List<Medicine>,
    val doseLogs: List<DoseLog>
)

fun encodeBackup(medicines: List<Medicine>, doseLogs: List<DoseLog>): String {
    val root = JSONObject()
        .put("format", BACKUP_FORMAT)
        .put("version", BACKUP_VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("medicines", JSONArray().apply {
            medicines.forEach { medicine ->
                put(
                    JSONObject()
                        .put("id", medicine.id)
                        .put("name", medicine.name)
                        .put("defaultAmount", medicine.defaultAmount)
                        .put("unit", medicine.unit)
                        .put("presetAmount1", medicine.presetAmount1)
                        .put("presetAmount2", medicine.presetAmount2)
                        .put("presetAmount3", medicine.presetAmount3)
                        .put("sortOrder", medicine.sortOrder)
                        .apply { medicine.dailyMaxAmount?.let { put("dailyMaxAmount", it) } }
                        .apply { medicine.colorKey?.let { put("colorKey", it) } }
                )
            }
        })
        .put("doseLogs", JSONArray().apply {
            doseLogs.forEach { log ->
                put(
                    JSONObject()
                        .put("id", log.id)
                        .put("medicineId", log.medicineId)
                        .put("medicineName", log.medicineName)
                        .put("amount", log.amount)
                        .put("unit", log.unit)
                        .put("timestamp", log.timestamp)
                )
            }
        })

    return root.toString(2)
}

fun decodeBackup(text: String): BackupData {
    val root = JSONObject(text)
    val format = root.optString("format", BACKUP_FORMAT)
    require(format == BACKUP_FORMAT) { "This is not a MedTracker backup." }

    val version = root.optInt("version", 0)
    require(version == BACKUP_VERSION) { "Unsupported backup version: $version." }

    val medicines = root.requireArray("medicines").mapObjectsIndexed { index, json ->
        val defaultAmount = json.getDouble("defaultAmount")
        Medicine(
            id = json.getLong("id"),
            name = json.getString("name"),
            defaultAmount = defaultAmount,
            unit = json.getString("unit"),
            presetAmount1 = json.optPreset("presetAmount1", defaultAmount * 0.5),
            presetAmount2 = json.optPreset("presetAmount2", defaultAmount),
            presetAmount3 = json.optPreset("presetAmount3", defaultAmount * 2),
            dailyMaxAmount = json.optNullableDouble("dailyMaxAmount"),
            colorKey = json.optNullableString("colorKey"),
            // Backups written before sort orders existed keep their array order.
            sortOrder = json.optInt("sortOrder", index)
        )
    }

    val logsArray = root.optJSONArray("doseLogs") ?: root.requireArray("logs")
    val doseLogs = logsArray.mapObjects { json ->
        DoseLog(
            id = json.getLong("id"),
            medicineId = json.getLong("medicineId"),
            medicineName = json.getString("medicineName"),
            amount = json.getDouble("amount"),
            unit = json.getString("unit"),
            timestamp = json.getLong("timestamp")
        )
    }

    validateBackup(medicines, doseLogs)
    return BackupData(medicines = medicines, doseLogs = doseLogs)
}

private fun JSONObject.requireArray(name: String): JSONArray =
    optJSONArray(name) ?: error("Backup is missing '$name'.")

private fun JSONObject.optPreset(name: String, defaultValue: Double): Double =
    if (has(name)) getDouble(name) else defaultValue

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) getDouble(name) else null

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    List(length()) { index -> block(getJSONObject(index)) }

private fun <T> JSONArray.mapObjectsIndexed(block: (Int, JSONObject) -> T): List<T> =
    List(length()) { index -> block(index, getJSONObject(index)) }

private fun validateBackup(medicines: List<Medicine>, doseLogs: List<DoseLog>) {
    require(medicines.distinctBy { it.id }.size == medicines.size) {
        "Backup contains duplicate medicine ids."
    }
    require(doseLogs.distinctBy { it.id }.size == doseLogs.size) {
        "Backup contains duplicate dose log ids."
    }
    medicines.forEach { medicine ->
        require(medicine.id > 0) { "Backup contains a medicine without an id." }
        require(medicine.name.isNotBlank()) { "Backup contains a medicine without a name." }
        require(medicine.unit.isNotBlank()) { "Backup contains a medicine without a unit." }
        require(medicine.defaultAmount > 0) { "Backup contains an invalid default dose." }
        require(medicine.presetAmounts().all { it > 0 }) {
            "Backup contains an invalid quick dose."
        }
        medicine.dailyMaxAmount?.let {
            require(it > 0) { "Backup contains an invalid daily maximum." }
        }
        medicine.colorKey?.let {
            require(it.isNotBlank()) { "Backup contains an invalid medicine color." }
        }
    }
    doseLogs.forEach { log ->
        require(log.id > 0) { "Backup contains a dose log without an id." }
        require(log.medicineId > 0) { "Backup contains a dose log without a medicine id." }
        require(log.medicineName.isNotBlank()) { "Backup contains a dose log without a medicine name." }
        require(log.unit.isNotBlank()) { "Backup contains a dose log without a unit." }
        require(log.amount > 0) { "Backup contains an invalid dose amount." }
        require(log.timestamp > 0) { "Backup contains an invalid timestamp." }
    }
}
