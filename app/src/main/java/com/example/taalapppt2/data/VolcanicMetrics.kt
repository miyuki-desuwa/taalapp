// src/main/java/com/example/taalapppt2/data/VolcanicMetrics.kt
package com.example.taalapppt2.data

/**
 * Data class to hold parsed and structured volcanic metrics extracted from the raw content.
 * All fields are nullable because not all data might be present or easily parsable.
 */
data class VolcanicMetrics(
    val alertLevel: Int? = null,
    val eruptionChance: String? = null, // e.g., "5%"
    val seismicityDescription: String? = null, // e.g., "2 volcanic earthquakes occurred today"
    val so2Flux: String? = null, // e.g., "505 tons/day (10 March 2025)"
    val plumeDescription: String? = null, // e.g., "500 meters high; Light evaporation; stranded in the southwest..."
    val groundDeformation: String? = null, // e.g., "Long-term subsidence..."
    val mainCraterLakeTemp: String? = null, // e.g., "71.3 °C (19 February 2025)"
    val mainCraterLakeAcidity: String? = null, // e.g., "0.3 (19 February 2025)"
    val earthquakeForecast: String? = null, // e.g., "4% Earthquake Forecast" (from homepage)
    val ashfallForecast: String? = null, // e.g., "1% Ashfall Forecast" (from homepage)
    val smogForecast: String? = null, // e.g., "7% Smog Forecast" (from homepage)
    val warningMessage: String? = null // e.g., "Sudden steam or phreatic explosions..." (from report screen)
)

// You can keep LinkData and IframeContent in your service package or move them here.
// For consistency with your current `service` file, let's assume they stay there for now.