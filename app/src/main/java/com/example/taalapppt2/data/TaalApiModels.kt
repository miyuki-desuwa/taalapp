package com.example.taalapppt2.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TaalApiResponse(
    val data: List<List<String>>,
    val headers: List<String>,
    val success: Boolean
)

data class TaalReportEntry(
    val date: String,
    val alertLevel: String,
    val eruption: String,
    val seismicity: String,
    val acidity: String,
    val temperature: String,
    val sulfurDioxideFlux: String,
    val plume: String,
    val groundDeformation: String
) {
    fun getParsedDate(): Date? {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.US)
        return try {
            dateFormat.parse(date)
        } catch (e: Exception) {
            null
        }
    }

    fun getAlertLevelInt(): Int? {
        return try {
            alertLevel.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun getTemperatureValue(): Double? {
        val regex = "(\\d+\\.?\\d*)\\s*\u2103".toRegex()
        return regex.find(temperature)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun getTemperatureDate(): String {
        val regex = "\\(([^)]+)\\)".toRegex()
        return regex.find(temperature)?.groupValues?.getOrNull(1) ?: "Date N/A"
    }

    fun getAcidityValue(): Double? {
        val regex = "(\\d+\\.?\\d*)".toRegex()
        return regex.find(acidity)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun getAcidityDate(): String {
        val regex = "\\(([^)]+)\\)".toRegex()
        return regex.find(acidity)?.groupValues?.getOrNull(1) ?: "Date N/A"
    }

    fun getPlumeHeightM(): Int? {
        val regex = "(\\d+)\\s*meters tall".toRegex()
        return regex.find(plume)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun getPlumeEmissionStrength(): String {
        return when {
            plume.contains("Moderate emission", ignoreCase = true) -> "Moderate"
            plume.contains("Weak emission", ignoreCase = true) -> "Weak"
            else -> "Unknown"
        }
    }

    fun getPlumeDriftDirection(): String {
        val regex = "([a-zA-Z]+\\s*drift)".toRegex()
        return regex.find(plume)?.groupValues?.getOrNull(1)?.replace(" drift", "") ?: "Unknown"
    }

    fun getVolcanicEarthquakesCount(): Int? {
        val regex = "(\\d+)\\s*volcanic earthquakes".toRegex()
        return regex.find(seismicity)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun getVolcanicTremorsCount(): Int? {
        val regex = "including\\s*(\\d+)\\s*volcanic tremors".toRegex()
        return regex.find(seismicity)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun getVolcanicTremorsDuration(): String {
        val regex = "\\(([^)]+)\\)".toRegex() // Matches text inside parentheses
        return regex.find(seismicity)?.groupValues?.getOrNull(1) ?: "Duration N/A"
    }

    fun hasEruption(): Boolean {
        return eruption != "0" && eruption.isNotBlank()
    }

    fun getSo2FluxValue(): Double? {
        val cleanedFlux = sulfurDioxideFlux.replace(",", "").substringBefore(" tonnes").trim()
        return cleanedFlux.toDoubleOrNull()
    }


    fun getSo2FluxDate(): String {
        val regex = "\\(([^)]+)\\)".toRegex()
        return regex.find(sulfurDioxideFlux)?.groupValues?.getOrNull(1) ?: "Date N/A"
    }
}