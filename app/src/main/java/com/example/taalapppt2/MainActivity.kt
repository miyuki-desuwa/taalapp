package com.example.taalapppt2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

import com.example.taalapppt2.ui.theme.Taalapppt2Theme


val MutePrimary = Color(0xFF555555)
val MuteLightBackground = Color(0xFFF0F0F0)
val MuteMidBackground = Color(0xFFE0E0E0)
val MuteCardBackground = Color(0xFFFFFFFF)
val MuteTextDark = Color.Black
val MuteTextLight = Color.White
val AlertRed = Color(0xFFAA0000)



const val CHANNEL_ID = "taal_monitoring_channel"
const val CHANNEL_NAME = "Taal Monitoring Alerts"
const val CHANNEL_DESCRIPTION = "Alerts and updates from Taal Monitoring App"
const val NOTIFICATION_ID_URGENT_ALERT = 1002

data class DailyForecast(
    val earthquakeTitle: String,
    val earthquakeDescription: String,
    val ashfallTitle: String,
    val ashfallDescription: String,
    val smogTitle: String,
    val smogDescription: String
)

data class TaalForecastData(
    val date: Date,
    val alertLevel: Int,
    val acidityPh: Double,
    val craterTemperatureC: Double,
    val so2FluxTpd: Double,
    val plumeHeightM: Int,
    val plumeDriftDirection: String,
    val plumeStrength: Int,
    val volcanicEarthquakes: Double,
    val totalTremorDurationMin: Double,
    val calderaTrend: Int,
    val stInflation: Int,
    val stDeflation: Int,
    val ltInflation: Int,
    val ltDeflation: Int

)

// NEW: Placeholder data for each date selection
val todayForecastData = DailyForecast(
    earthquakeTitle = "Earthquake Forecast: Low",
    earthquakeDescription = "Minor tremors recorded, no significant threat today.",
    ashfallTitle = "Ashfall Forecast: Minimal",
    ashfallDescription = "Minor ash emissions observed, minimal impact expected today.",
    smogTitle = "Smog Forecast: Moderate",
    smogDescription = "Slight increase in pollutants today, sensitive individuals should take precautions."
)

val tomorrowForecastData = DailyForecast(
    earthquakeTitle = "Earthquake Forecast: Moderate",
    earthquakeDescription = "Increased seismic activity expected tomorrow, remain vigilant.",
    ashfallTitle = "Ashfall Forecast: Possible",
    ashfallDescription = "Light ashfall possible tomorrow, carry masks when outdoors.",
    smogTitle = "Smog Forecast: High",
    smogDescription = "Elevated pollutant levels expected tomorrow, avoid outdoor activities."
)

val threeDaysForecastData = DailyForecast(
    earthquakeTitle = "Earthquake Forecast: Low",
    earthquakeDescription = "Seismic activity expected to normalize over the next 3 days.",
    ashfallTitle = "Ashfall Forecast: None",
    ashfallDescription = "No significant ashfall expected in the next 3 days.",
    smogTitle = "Smog Forecast: Low",
    smogDescription = "Improved air quality projected for the next 3 days."
)


class MainActivity : ComponentActivity() {


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {

            } else {
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            Taalapppt2Theme {
                TaalMonitoringApp()
            }
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for alerts
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION

            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaalMonitoringApp() {
    val context = LocalContext.current
    var selectedDateButton by remember { mutableIntStateOf(0) }
    var currentDateTimeString by remember { mutableStateOf("Loading date...") }
    var selectedScreen by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, HH:mm")
        val philippineZone = ZoneId.of("Asia/Manila")
        while (true) {
            currentDateTimeString = LocalDateTime.now(philippineZone).format(formatter)
            delay(1000) // Update every second
        }
    }

    // NEW: Select the current forecast data based on selectedDateButton
    val currentForecast: DailyForecast = remember(selectedDateButton) {
        when (selectedDateButton) {
            0 -> todayForecastData
            1 -> tomorrowForecastData
            2 -> threeDaysForecastData
            else -> todayForecastData
        }
    }

    val density = LocalDensity.current

    val imageContentSectionHeight = 350.dp
    val titleSectionHeight = with(density) {
        (24.sp.toDp() + 16.dp + 8.dp)
    }

    Scaffold(
        topBar = {

        },
        bottomBar = {
            TaalMonitoringBottomBar(
                selectedItem = selectedScreen,
                onItemSelected = { index -> selectedScreen = index }
            )
        },
        containerColor = MuteLightBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->

        when (selectedScreen) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = paddingValues.calculateBottomPadding())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                imageContentSectionHeight +
                                        titleSectionHeight +
                                        with(density) { WindowInsets.systemBars.getTop(density).toDp() }
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mountain_lake_background),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        ) {
                            Text(
                                text = "Taal Monitoring",
                                color = MuteTextLight,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(imageContentSectionHeight)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.volcano),
                                            contentDescription = "Volcano",
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Weak Evaporation",
                                            color = MuteTextLight,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.align(Alignment.Start)
                                    ) {
                                        Text(
                                            text = "5%",
                                            fontSize = 80.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MuteTextLight,
                                            lineHeight = 80.sp
                                        )
                                        Text(
                                            text = "Chance of Eruption",
                                            fontSize = 20.sp,
                                            color = MuteTextLight
                                        )
                                    }

                                    Text(
                                        text = currentDateTimeString,
                                        fontSize = 16.sp,
                                        color = MuteTextLight,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MuteMidBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 5.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            DateSelectionButton(
                                text = "Today",
                                isSelected = selectedDateButton == 0,
                                onClick = { selectedDateButton = 0 }
                            )
                            DateSelectionButton(
                                text = "Tomorrow",
                                isSelected = selectedDateButton == 1,
                                onClick = { selectedDateButton = 1 }
                            )
                            DateSelectionButton(
                                text = "3 days",
                                isSelected = selectedDateButton == 2,
                                onClick = { selectedDateButton = 2 }
                            )
                        }

                        PredictionCard(
                            iconResId = R.drawable.earthquake,
                            predictionTitle = currentForecast.earthquakeTitle,
                            description = currentForecast.earthquakeDescription
                        )
                        PredictionCard(
                            iconResId = R.drawable.ashfall,
                            predictionTitle = currentForecast.ashfallTitle,
                            description = currentForecast.ashfallDescription
                        )
                        PredictionCard(
                            iconResId = R.drawable.smog,
                            predictionTitle = currentForecast.smogTitle,
                            description = currentForecast.smogDescription
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            1 -> {
                Box(modifier = Modifier.padding(paddingValues)) {
                    ActivityReportScreen(onBackClick = { selectedScreen = 0 })
                }
            }
            2 -> {
                Box(modifier = Modifier.padding(paddingValues)) {
                    NotificationScreen(onBackClick = { selectedScreen = 0 })
                }
            }
            else -> {
                Box(modifier = Modifier.padding(paddingValues)) {
                    Text("Invalid Screen", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun RowScope.DateSelectionButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MutePrimary else MuteCardBackground
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .padding(horizontal = 4.dp)
    ) {
        Text(text, color = if (isSelected) MuteTextLight else MuteTextDark, fontSize = 13.sp)
    }
}

@Composable
fun PredictionCard(iconResId: Int, predictionTitle: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MuteCardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(predictionTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MuteTextDark)
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MuteTextDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(description, fontSize = 14.sp, color = MuteTextDark)
        }
    }
}

@Composable
fun TaalMonitoringBottomBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = MuteCardBackground,
        modifier = Modifier.height(60.dp)
    ) {
        NavigationBarItem(
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MutePrimary,
                selectedTextColor = MutePrimary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) },
            icon = { Icon(Icons.Default.Edit, contentDescription = "Report") },
            label = { Text("Report", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MutePrimary,
                selectedTextColor = MutePrimary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Notification") },
            label = { Text("Notification", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MutePrimary,
                selectedTextColor = MutePrimary,
                indicatorColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityReportScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var latestTaalData by remember { mutableStateOf<TaalForecastData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("taal_cleaned_forecast_ready.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))

            val lines = reader.readLines()
            if (lines.size <= 1) { // Only header or empty
                error = "CSV file is empty or contains only header."
            } else {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val allData = mutableListOf<TaalForecastData>()

                // Skip header line
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val tokens = line.split(",")
                    if (tokens.size >= 25) { // Ensure enough columns
                        try {
                            // Parses data
                            val date = dateFormat.parse(tokens[0])
                            val alertLevel = tokens[1].toInt()
                            val acidityPh = tokens[2].toDouble()
                            val craterTemperatureC = tokens[3].toDouble()
                            val so2FluxTpd = tokens[4].toDouble()
                            val plumeHeightM = tokens[5].toInt()
                            val plumeDriftDirection = tokens[6]
                            val plumeStrength = tokens[7].toInt()
                            val eruptionCount = tokens[8].toDouble() // Not used
                            val eruptionSeverityScore = tokens[9].toDouble() // Not used
                            val totalEruptionDurationMin = tokens[10].toDouble() // Not used
                            val avgEruptionDurationMin = tokens[11].toDouble() // Not used
                            val volcanicEarthquakes = tokens[12].toDouble()
                            val volcanicTremors = tokens[13].toDouble() // Not used
                            val totalTremorDurationMin = tokens[14].toDouble()
                            val hasLongTremor = tokens[15].toDouble().toInt()  // Not used
                            val hasWeakTremor = tokens[16].toDouble().toInt() // Not used
                            val calderaTrend = tokens[17].toInt()
                            val tviTrend = tokens[18].toInt() // Not used
                            val northTrend = tokens[19].toInt() // Not used
                            val seTrend = tokens[20].toInt() // Not used
                            val ltInflation = tokens[21].toInt()
                            val ltDeflation = tokens[22].toInt()
                            val stInflation = tokens[23].toInt()
                            val stDeflation = tokens[24].toInt()


                            allData.add(
                                TaalForecastData(
                                    date, alertLevel, acidityPh, craterTemperatureC, so2FluxTpd,
                                    plumeHeightM, plumeDriftDirection, plumeStrength,
                                    volcanicEarthquakes, totalTremorDurationMin, calderaTrend,
                                    stInflation, stDeflation, ltInflation, ltDeflation
                                )
                            )
                        } catch (e: Exception) {

                            println("Error parsing CSV line: $line - ${e.message}")
                            e.printStackTrace()
                            continue
                        }
                    }
                }
                // Sort by date in descending order to get the latest data
                latestTaalData = allData.sortedByDescending { it.date }.firstOrNull()
                if (latestTaalData == null) {
                    error = "No valid data rows found in CSV."
                }
            }

        } catch (e: Exception) {
            error = "An unexpected error occurred during CSV parsing: ${e.message}"
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Taal Activity Report",
                        color = MuteTextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MuteTextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MuteLightBackground
                )
            )
        },
        containerColor = MuteLightBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .background(MuteMidBackground)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Loading latest data...", color = MuteTextDark)
            }

             else if (latestTaalData != null) {
                val data = latestTaalData!!
                val dateFormatDisplay = SimpleDateFormat("dd MMMM yyyy", Locale.US)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MutePrimary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ALERT LEVEL",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MuteTextLight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = data.alertLevel.toString(),
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black,
                            color = MuteTextLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))


                ReportDataCard(
                    iconResId = R.drawable.seismicity,
                    title = "Seismicity",
                    description = "${data.volcanicEarthquakes.toInt()} volcanic earthquakes occurred today"
                )

                ReportDataCard(
                    iconResId = 0,
                    title = "Warning",
                    description = """
                        - Sudden steam or phreatic explosions
                        - Volcanic earthquakes
                        - Light ashfall
                        - Accumulation or emission of toxic gases
                        """.trimIndent(),
                    isWarning = true
                )

                ReportDataCard(
                    iconResId = R.drawable.sodium_dioxide,
                    title = "Sulfur Dioxide Flux",
                    description = "${data.so2FluxTpd} tons/day (${dateFormatDisplay.format(data.date)})"
                )

                ReportDataCard(
                    iconResId = R.drawable.plume,
                    title = "Plume",
                    description = "${data.plumeHeightM} meters high; ${if(data.plumeStrength == 0) "Weak" else "Strong"} evaporation; stranded in the ${data.plumeDriftDirection.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}; exercise caution as visibility may be affected and mild ashfall is possible"
                )

                ReportDataCard(
                    iconResId = R.drawable.ground_deformation,
                    title = "Ground Deformation",
                    description = "Long-term ${if(data.ltDeflation == 1) "subsidence" else if(data.ltInflation == 1) "inflation" else "stable"} of the larger Taal Caldera with short-term ${if(data.stInflation == 1) "swelling" else if(data.stDeflation == 1) "deflation" else "stable"} of the southeastern part of Taal Volcano Island"
                )

                ReportDataCard(
                    iconResId = R.drawable.temperature,
                    title = "Temperature",
                    description = "Main Crater Lake\n${data.craterTemperatureC} °C (${dateFormatDisplay.format(data.date)})"
                )

                ReportDataCard(
                    iconResId = R.drawable.ph_scale,
                    title = "Acidity",
                    description = "Main Crater Lake\n${data.acidityPh} (${dateFormatDisplay.format(data.date)})"
                )

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text("No data available from CSV.", color = MuteTextDark)
            }
        }
    }
}

@Composable
fun ReportDataCard(
    iconResId: Int,
    title: String,
    description: String,
    isWarning: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) Color(0xFFF8E71C).copy(alpha = 0.8f) else MuteCardBackground
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isWarning) {

                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = MuteTextDark,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (iconResId != 0) {

                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MuteTextDark
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MuteTextDark
            )
        }
    }
}

@Composable
fun PriorityAlertCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Priority Alert",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Priority Alert Announcement:",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MuteTextDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "VOLCANIC ERUPTION IN PROGRESS!",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFFD32F2F),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MuteTextDark,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RegularNotificationCard(title: String, description: String, backgroundColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = MuteTextDark
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MuteTextDark
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    val philippineZone = remember { ZoneId.of("Asia/Manila") }

    val todayDate = remember { LocalDate.now(philippineZone) }
    val yesterdayDate = remember { todayDate.minusDays(1) }
    val twoDaysAgoDate = remember { todayDate.minusDays(2) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        color = MuteTextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MuteTextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MuteLightBackground
                )
            )
        },
        containerColor = MuteLightBackground

    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .background(MuteMidBackground)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = dateFormatter.format(todayDate),
                fontWeight = FontWeight.Bold,
                color = MutePrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PriorityAlertCard(
                message = """
                    Emergency Warning! A major volcanic eruption has been detected. The volcano is actively erupting, sending ash, lava, and gases into the atmosphere. Residents in the affected areas must take immediate precautions:

                    Seek Shelter Immediately - Stay indoors, close windows and doors, and use masks or damp cloths to protect yourself from ash inhalation.

                    Ashfall Expected - Air quality may be hazardous. Limit outdoor activities, protect water sources, and avoid unnecessary travel.

                    This is a high-priority emergency. Take action now to ensure your safety.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = dateFormatter.format(yesterdayDate),
                fontWeight = FontWeight.Bold,
                color = MutePrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RegularNotificationCard(
                title = "Toxic Gas Alert!",
                description = "High levels of sulfur dioxide detected wear a mask and avoid low-lying areas.",
                backgroundColor = Color(0xFFF9E6E6)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = dateFormatter.format(twoDaysAgoDate),
                fontWeight = FontWeight.Bold,
                color = MutePrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RegularNotificationCard(
                title = "Visibility Reduced Due to Volcanic Smog!",
                description = "Use headlights when driving and avoid unnecessary travel.",
                backgroundColor = Color(0xFFF9E6E6)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Taalapppt2Theme {
        TaalMonitoringApp()
    }
}