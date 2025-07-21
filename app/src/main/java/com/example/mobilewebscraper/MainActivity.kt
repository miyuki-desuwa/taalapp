package com.example.mobilewebscraper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.example.mobilewebscraper.data.VolcanicData
import com.example.mobilewebscraper.data.LinkData
import com.example.mobilewebscraper.data.IframeContent
import com.example.mobilewebscraper.ui.theme.MobileWebScraperTheme
import com.example.mobilewebscraper.viewmodel.VolcanoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileWebScraperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VolcanoScraperApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoScraperApp() {
    val viewModel: VolcanoViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var urlInput by remember { mutableStateOf("https://www.phivolcs.dost.gov.ph/") }
    var deepScrapeEnabled by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volcano Activity Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL Input Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter Volcano Monitoring URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Deep Scrape Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deepScrapeEnabled,
                            onCheckedChange = { deepScrapeEnabled = it }
                        )
                        Text(
                            text = "Enable Deep Scraping (Extract iframe content)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.scrapeUrl(urlInput, deepScrapeEnabled) },
                        enabled = !uiState.isLoading && urlInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (uiState.isLoading) "Scraping..." else "Scrape Data")
                    }
                }
            }
            
            // Error Display
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Scraped Data Display
            uiState.scrapedData?.let { data ->
                if (data.isSuccess) {
                    // Main Volcanic Data
                    VolcanicDataCard(data.volcanicData, data.title, data.lastUpdated)
                    
                    // Links Section
                    if (data.links.isNotEmpty()) {
                        LinksCard(data.links)
                    }
                    
                    // Iframe Content Section
                    data.iframeContent?.let { iframeContent ->
                        IframeContentCard(iframeContent)
                    }
                    
                    // Metadata Section
                    if (data.metadata.isNotEmpty()) {
                        MetadataCard(data.metadata)
                    }
                }
            }
        }
    }
}

@Composable
fun LinksCard(links: List<LinkData>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = "Links")
                Text(
                    text = "Found Links (${links.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Divider()
            links.forEach { link ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = link.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (link.isExternal) {
                            Text(
                                text = "External Link",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IframeContentCard(iframeContent: IframeContent) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Iframe Content",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Source: ${iframeContent.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Divider()
            
            // Show error if any
            iframeContent.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Show volcanic data from iframe
            iframeContent.volcanicData?.let { volcanicData ->
                Text(
                    text = "Volcanic Data from Iframe:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                VolcanicDataCard(volcanicData, "Iframe Data", "")
            }
            
            // Show images
            if (iframeContent.images.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Images")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Images Found: ${iframeContent.images.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                iframeContent.images.take(3).forEach { imageUrl ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = imageUrl,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (iframeContent.images.size > 3) {
                    Text(
                        text = "... and ${iframeContent.images.size - 3} more images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataCard(metadata: Map<String, Any>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Request Metadata",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            metadata.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun VolcanicDataCard(volcanicData: VolcanicData, title: String, lastUpdated: String) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Last Updated: $lastUpdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Divider()
            
            // Alert Level
            volcanicData.alertLevel?.let { level ->
                AlertLevelCard(level)
            }
            
            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                volcanicData.volcanicEarthquakes?.let { earthquakes ->
                    MetricCard(
                        title = "Earthquakes",
                        value = earthquakes.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                volcanicData.gasEmissions?.let { emissions ->
                    MetricCard(
                        title = "Gas Emissions",
                        value = "${emissions.toInt()} t/day",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Additional Info
            volcanicData.plumeActivity?.let { plume ->
                InfoCard("Plume Activity", plume)
            }
            
            // Observations
            if (volcanicData.observations.isNotEmpty()) {
                Text(
                    text = "Key Observations:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                volcanicData.observations.forEach { observation ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "• ${observation.replaceFirstChar { it.uppercase() }}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertLevelCard(level: Int) {
    val (color, description) = when (level) {
        0 -> MaterialTheme.colorScheme.primary to "Normal"
        1 -> MaterialTheme.colorScheme.secondary to "Abnormal"
        2 -> Color(0xFFFF9800) to "Increased Unrest"
        3 -> Color(0xFFFF5722) to "Magmatic Unrest"
        4 -> MaterialTheme.colorScheme.error to "Hazardous Eruption"
        5 -> Color(0xFF8B0000) to "Hazardous Eruption"
        else -> MaterialTheme.colorScheme.outline to "Unknown"
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Alert Level",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}