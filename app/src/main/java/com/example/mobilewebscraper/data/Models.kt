package com.example.mobilewebscraper.data

data class ScrapedData(
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val title: String = "",
    val lastUpdated: String = "",
    val volcanicData: VolcanicData = VolcanicData(),
    val links: List<LinkData> = emptyList(),
    val iframeContent: IframeContent? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class VolcanicData(
    val alertLevel: Int? = null,
    val volcanicEarthquakes: Int? = null,
    val gasEmissions: Double? = null,
    val plumeActivity: String? = null,
    val observations: List<String> = emptyList()
)

data class LinkData(
    val text: String,
    val url: String,
    val isExternal: Boolean = false
)

data class IframeContent(
    val url: String,
    val error: String? = null,
    val volcanicData: VolcanicData? = null,
    val images: List<String> = emptyList()
) 