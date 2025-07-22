package com.example.taalapppt2.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import com.example.taalapppt2.data.VolcanicMetrics


data class LinkData(
    val text: String,
    val url: String,
    val isExternal: Boolean = false
)


data class IframeContent(
    val title: String,
    val src: String
)


data class ScrapedData(
    val title: String,
    val rawContent: String,
    val links: List<LinkData>,
    val iframes: List<IframeContent>,
    val timestamp: LocalDateTime,
    val parsedMetrics: VolcanicMetrics? = null
)

suspend fun fetchVolcanoData(): List<ScrapedData> = withContext(Dispatchers.IO) {
    val scrapedDataList = mutableListOf<ScrapedData>()


    val url = "https://phivolcs.dost.gov.ph/index.php/volcano-hazard/volcano-bulletin2/taal-volcano/32486-bulkang-taal-buod-ng-24-oras-na-pagmamanman-22-hulyo-2025-alas-12-ng-umaga"
    val doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .timeout(10000)
        .get()


    val contentBlocks = doc.select(".content-category div[itemprop=articleBody]")


    val parsedMetrics = parseMetricsFromDocument(doc)

    if (contentBlocks.isEmpty()) {

        val title = doc.title()
        val rawContent = doc.body().text()
        val links = doc.select("a[href]").map {
            LinkData(text = it.text(), url = it.absUrl("href"), isExternal = !it.absUrl("href").startsWith(url))
        }
        val iframes = doc.select("iframe[src]").map {
            IframeContent(title = it.attr("title"), src = it.absUrl("src"))
        }

        scrapedDataList.add(
            ScrapedData(
                title = title,
                rawContent = rawContent,
                links = links,
                iframes = iframes,
                timestamp = LocalDateTime.now(),
                parsedMetrics = parsedMetrics
            )
        )
    } else {

        for (block in contentBlocks) {
            val title = doc.title()
            val rawContent = block.text()

            val links = block.select("a[href]").map {
                LinkData(text = it.text(), url = it.absUrl("href"), isExternal = !it.absUrl("href").startsWith(url))
            }

            val iframes = block.select("iframe[src]").map {
                IframeContent(title = it.attr("title"), src = it.absUrl("src"))
            }

            val scrapedData = ScrapedData(
                title = title,
                rawContent = rawContent,
                links = links,
                iframes = iframes,
                timestamp = LocalDateTime.now(),
                parsedMetrics = parsedMetrics
            )
            scrapedDataList.add(scrapedData)
        }
    }
    scrapedDataList
}


fun parseMetricsFromDocument(doc: Document): VolcanicMetrics {
    var alertLevel: Int? = null
    var eruptionChance: String? = null
    var seismicityDescription: String? = null
    var so2Flux: String? = null
    var plumeDescription: String? = null
    var groundDeformation: String? = null
    var mainCraterLakeTemp: String? = null
    var mainCraterLakeAcidity: String? = null
    var earthquakeForecast: String? = null
    var ashfallForecast: String? = null
    var smogForecast: String? = null
    var warningMessage: String? = null


    val alertLevelElement = doc.select("div.circle[title]").firstOrNull()
    alertLevel = alertLevelElement?.attr("title")?.toIntOrNull()


    val seismicityCountElement = doc.select("p.txt-no-eq.bold.newfont").firstOrNull()
    if (seismicityCountElement != null) {
        val countText = seismicityCountElement.text().trim().replace("\"", "")
        val count = countText.toIntOrNull()
        if (count != null) {
            seismicityDescription = "$count volcanic earthquakes occurred today"
        } else {
            seismicityDescription = countText // Fallback if not a number
        }
    }


    val so2LabelTd = doc.select("td:has(p > b:contains(Sulfur Dioxide Flux))").firstOrNull()
    if (so2LabelTd != null) {
        val so2ValueP = so2LabelTd.nextElementSibling()?.select("p.bold.txtleft.newfont")?.firstOrNull()
        so2Flux = so2ValueP?.text()?.trim()
    }


    val plumeLabelTd = doc.select("td:has(b:contains(Plume))").firstOrNull()
    if (plumeLabelTd != null) {
        val plumeValueP = plumeLabelTd.nextElementSibling()?.select("p.bold.txtleft.newfont")?.firstOrNull()
        plumeDescription = plumeValueP?.text()?.trim()
    }


    val groundDeformationLabelTd = doc.select("td:has(b:contains(Ground Deformation))").firstOrNull()
    if (groundDeformationLabelTd != null) {
        val groundDeformationValueP = groundDeformationLabelTd.nextElementSibling()?.select("p.bold.txtleft.newfont")?.firstOrNull()
        groundDeformation = groundDeformationValueP?.text()?.trim()
    }



    val tempLabelTd = doc.select("td:has(b:contains(Temperatura))").firstOrNull()
    if (tempLabelTd != null) {
        val tempValueP = tempLabelTd.nextElementSibling()?.select("p.bold.txtleft.newfont")?.firstOrNull()
        mainCraterLakeTemp = tempValueP?.text()?.trim()
    }



    val acidityLabelTd = doc.select("td:has(b:contains(Acidity))").firstOrNull()
    if (acidityLabelTd != null) {
        val acidityValueP = acidityLabelTd.nextElementSibling()?.select("p.bold.txtleft.newfont")?.firstOrNull()
        mainCraterLakeAcidity = acidityValueP?.text()?.trim()
    }



    val warningSection = doc.select(".content-category div[itemprop=articleBody]:contains(WARNING:)").firstOrNull()
    if (warningSection != null) {
        warningMessage = warningSection.text()
    }

    return VolcanicMetrics(
        alertLevel = alertLevel,
        eruptionChance = eruptionChance,
        seismicityDescription = seismicityDescription,
        so2Flux = so2Flux,
        plumeDescription = plumeDescription,
        groundDeformation = groundDeformation,
        mainCraterLakeTemp = mainCraterLakeTemp,
        mainCraterLakeAcidity = mainCraterLakeAcidity,
        earthquakeForecast = earthquakeForecast,
        ashfallForecast = ashfallForecast,
        smogForecast = smogForecast,
        warningMessage = warningMessage
    )
}