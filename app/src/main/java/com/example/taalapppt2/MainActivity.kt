package com.example.taalapppt2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taalapppt2.data.VolcanicMetrics // Import your data class
import com.example.taalapppt2.service.LinkData
import com.example.taalapppt2.service.ScrapedData // Import ScrapedData
import com.example.taalapppt2.ui.theme.TaalAppPT2Theme
import com.example.taalapppt2.viewmodel.VolcanoViewModel // Import your ViewModel


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaalAppPT2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VolcanoDataScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolcanoDataScreen(volcanoViewModel: VolcanoViewModel = viewModel()) {
    // Collect states from the ViewModel
    val scrapedDataList by volcanoViewModel.volcanoData.collectAsState()
    val isLoading by volcanoViewModel.isLoading.collectAsState()
    val error by volcanoViewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taal Volcano Bulletin") },
                actions = {
                    IconButton(onClick = { volcanoViewModel.fetchData() }) {
                        Icon(Icons.Filled.Refresh, "Refresh Data")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text("Fetching volcano data...", modifier = Modifier.padding(top = 16.dp))
                }
                error != null -> {
                    Text("Error: ${error}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { volcanoViewModel.fetchData() }) {
                        Text("Retry")
                    }
                }
                scrapedDataList.isNotEmpty() -> {
                    LazyColumn {
                        items(scrapedDataList) { data ->
                            VolcanoBulletinCard(data)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                else -> {
                    Text("No data available. Tap refresh to fetch.")
                    Button(onClick = { volcanoViewModel.fetchData() }) {
                        Text("Fetch Data")
                    }
                }
            }
        }
    }
}

@Composable
fun VolcanoBulletinCard(scrapedData: ScrapedData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = scrapedData.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Display timestamp (consider formatting)
            Text(
                text = "Scraped: ${scrapedData.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            scrapedData.parsedMetrics?.let { metrics ->
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Volcanic Metrics:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                metrics.alertLevel?.let {
                    MetricRow("Alert Level:", it.toString())
                }
                metrics.seismicityDescription?.let {
                    MetricRow("Seismicity:", it)
                }
                metrics.so2Flux?.let {
                    MetricRow("SO2 Flux:", it)
                }
                metrics.plumeDescription?.let {
                    MetricRow("Plume:", it)
                }
                metrics.groundDeformation?.let {
                    MetricRow("Ground Deformation:", it)
                }
                metrics.mainCraterLakeTemp?.let {
                    MetricRow("Crater Lake Temp:", it)
                }
                metrics.mainCraterLakeAcidity?.let {
                    MetricRow("Crater Lake Acidity:", it)
                }
                metrics.warningMessage?.let {
                    Text(
                        text = "Warning: $it",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // If you later add eruptionChance, earthquakeForecast, etc., display them here
            }

            // Display raw content (optional, good for debugging/initial verification)
            // Text(
            //     text = "Raw Content (first 200 chars): ${scrapedData.rawContent.take(200)}...",
            //     style = MaterialTheme.typography.bodySmall,
            //     modifier = Modifier.padding(top = 8.dp)
            // )

            // Display links (if any)
            if (scrapedData.links.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Links:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                scrapedData.links.forEach { link ->
                    Text(
                        text = "- ${link.text} (${link.url})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Blue
                    )
                }
            }

            // Display iframes (if any) - might need a WebView for actual rendering
            if (scrapedData.iframes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Iframes:",
                    style = MaterialT`her`e.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                scrapedData.iframes.forEach { iframe ->
                    Text(
                        text = "- ${iframe.title} (${iframe.src})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
        Text(text = value)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TaalAppPT2Theme {
        VolcanoDataScreen()
    }
}