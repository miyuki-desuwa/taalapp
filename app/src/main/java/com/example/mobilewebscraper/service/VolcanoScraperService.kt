package com.example.mobilewebscraper.service

import com.example.mobilewebscraper.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class VolcanoScraperService {
    private val sslSocketFactory: SSLSocketFactory by lazy {
        // Create a trust manager that accepts all certificates
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    }

    suspend fun scrapeVolcanoData(url: String, deepScrape: Boolean): ScrapedData {
        return withContext(Dispatchers.IO) {
            try {
                // Configure Jsoup connection with custom SSL settings
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .sslSocketFactory(sslSocketFactory)
                    .followRedirects(true)
                    .get()
                
                val title = doc.title()
                val lastUpdated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                
                // Extract volcanic data
                val volcanicData = extractVolcanicData(doc)
                val links = extractLinks(doc)
                val iframeContent = if (deepScrape) extractIframeContent(doc) else null
                val metadata = extractMetadata(doc)
                
                ScrapedData(
                    isSuccess = true,
                    title = title,
                    lastUpdated = lastUpdated,
                    volcanicData = volcanicData,
                    links = links,
                    iframeContent = iframeContent,
                    metadata = metadata
                )
            } catch (e: Exception) {
                ScrapedData(
                    isSuccess = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }
    
    private fun extractVolcanicData(doc: Document): VolcanicData {
        try {
            // Extract alert level - look for specific patterns in the PHIVOLCS bulletin
            val alertLevelPattern = "Alert Level ([0-5])".toRegex()
            val alertLevel = alertLevelPattern.find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            
            // Extract volcanic earthquakes
            val earthquakePattern = "(\\d+)\\s+volcanic\\s+earthquake".toRegex(RegexOption.IGNORE_CASE)
            val earthquakes = earthquakePattern.find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            
            // Extract SO2 emissions
            val so2Pattern = "(\\d+(?:\\.\\d+)?)[\\s]*tons/day".toRegex()
            val gasEmissions = so2Pattern.find(doc.text())?.groupValues?.get(1)?.toDoubleOrNull()
            
            // Extract plume activity
            val plumePattern = "plume(?:s)?\\s+(?:that\\s+)?reach(?:ed|ing)?\\s+([\\d,]+)\\s*meters".toRegex(RegexOption.IGNORE_CASE)
            val plumeActivity = plumePattern.find(doc.text())?.let { match ->
                "${match.groupValues[1]} meters high"
            }
            
            // Extract observations
            val observations = mutableListOf<String>()
            val bulletinContent = doc.select(".article-content, .content, .bulletin-content").text()
            
            // Split into sentences and look for relevant observations
            bulletinContent.split(". ").forEach { sentence ->
                if (sentence.contains(Regex("seismic|volcanic|tremor|earthquake|emission|activity", RegexOption.IGNORE_CASE))) {
                    observations.add(sentence.trim() + ".")
                }
            }
            
            return VolcanicData(
                alertLevel = alertLevel,
                volcanicEarthquakes = earthquakes,
                gasEmissions = gasEmissions,
                plumeActivity = plumeActivity,
                observations = observations.take(5) // Limit to top 5 observations
            )
        } catch (e: Exception) {
            return VolcanicData() // Return empty data if parsing fails
        }
    }
    
    private fun extractLinks(doc: Document): List<LinkData> {
        return doc.select("a[href]").mapNotNull { element ->
            val href = element.attr("abs:href")
            val linkText = element.text().trim()
            
            // Check if the link text matches the Taal Volcano 24-hour observation pattern
            if (href.isNotBlank() && 
                linkText.matches(Regex(".*Taal\\s+Volcano\\s+(?:Summary\\s+of\\s+)?24[-\\s]?[Hh](?:ou)?r\\s+[Oo]bservation.*", RegexOption.IGNORE_CASE))) {
                
                val isExternal = try {
                    URL(href).host != URL(doc.baseUri()).host
                } catch (e: Exception) {
                    false
                }
                
                LinkData(
                    text = linkText,
                    url = href,
                    isExternal = isExternal
                )
            } else null
        }
    }
    
    private fun extractIframeContent(doc: Document): IframeContent? {
        val iframe = doc.select("iframe").firstOrNull() ?: return null
        val iframeUrl = iframe.attr("abs:src")
        
        return try {
            val iframeDoc = Jsoup.connect(iframeUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .sslSocketFactory(sslSocketFactory)
                .get()
            
            val volcanicData = extractVolcanicData(iframeDoc)
            val images = iframeDoc.select("img[src]").mapNotNull { img -> 
                img.attr("abs:src").takeIf { it.isNotBlank() }
            }
            
            IframeContent(
                url = iframeUrl,
                volcanicData = volcanicData,
                images = images
            )
        } catch (e: Exception) {
            IframeContent(
                url = iframeUrl,
                error = "Failed to load iframe content: ${e.message}"
            )
        }
    }
    
    private fun extractMetadata(doc: Document): Map<String, Any> {
        return mapOf(
            "charset" to (doc.charset()?.name() ?: "unknown"),
            "baseUri" to doc.baseUri(),
            "contentType" to (doc.connection()?.response()?.contentType() ?: "unknown"),
            "elements" to doc.select("*").size,
            "lastModified" to (doc.connection()?.response()?.header("Last-Modified") ?: "unknown")
        )
    }
}