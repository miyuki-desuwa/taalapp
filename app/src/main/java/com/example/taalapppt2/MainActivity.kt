package com.example.taalapppt2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import retrofit2.Retrofit
import com.google.gson.GsonBuilder
import retrofit2.converter.gson.GsonConverterFactory
import com.example.taalapppt2.network.TaalApiService
import com.example.taalapppt2.data.TaalReportEntry

import com.example.taalapppt2.ui.theme.Taalapppt2Theme


val MutePrimary = Color(0xFF555555)
val MuteLightBackground = Color(0xFFF0F0F0)
val MuteMidBackground = Color(0xFFE0E0E0)
val MuteCardBackground = Color(0xFFFFFFFF)
val MuteTextDark = Color.Black
val MuteTextLight = Color.White

data class DailyForecast(
    val earthquakeTitle: String,
    val earthquakeDescription: String,
    val ashfallTitle: String,
    val ashfallDescription: String,
    val smogTitle: String,
    val smogDescription: String
)


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Taalapppt2Theme {
                TaalMonitoringApp()
            }
        }
    }
}

@Composable
fun TaalMonitoringApp() {
    var selectedDateButton by remember { mutableIntStateOf(0) }
    var currentDateTimeString by remember { mutableStateOf("Loading date...") }
    var selectedScreen by remember { mutableIntStateOf(0) }

    var latestTaalDataHome by remember { mutableStateOf<TaalReportEntry?>(null) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var errorHome by remember { mutableStateOf<String?>(null) }

    val taalApiService = remember {
        Retrofit.Builder()
            .baseUrl("https://eye-taal-webhost.vercel.app/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(TaalApiService::class.java)
    }

    LaunchedEffect(selectedDateButton) {
        val formatter = DateTimeFormatter.ofPattern("MMMM dd")
        val philippineZone = ZoneId.of("Asia/Manila")
        val baseDate = LocalDate.now(philippineZone)
        val displayDate = when (selectedDateButton) {
            0 -> baseDate.plus(1, ChronoUnit.DAYS)
            1 -> baseDate.plus(2, ChronoUnit.DAYS)
            2 -> baseDate.plus(3, ChronoUnit.DAYS)
            else -> baseDate
        }
        currentDateTimeString = displayDate.format(formatter)
    }

    LaunchedEffect(Unit) {
        isLoadingHome = true
        errorHome = null
        try {
            val response = taalApiService.getLatestTaalData()
            if (response.success && response.data.isNotEmpty()) {
                val parsedReports = response.data.map { row ->
                    TaalReportEntry(
                        date = row.getOrNull(0) ?: "",
                        alertLevel = row.getOrNull(1) ?: "",
                        eruption = row.getOrNull(2) ?: "",
                        seismicity = row.getOrNull(3) ?: "",
                        acidity = row.getOrNull(4) ?: "",
                        temperature = row.getOrNull(5) ?: "",
                        sulfurDioxideFlux = row.getOrNull(6) ?: "",
                        plume = row.getOrNull(7) ?: "",
                        groundDeformation = row.getOrNull(8) ?: ""
                    )
                }
                latestTaalDataHome = parsedReports.firstOrNull()
            } else {
                errorHome = "API returned no data or was not successful."
            }
        } catch (e: Exception) {
            errorHome = "Failed to load home data: ${e.message}"
            e.printStackTrace()
        } finally {
            isLoadingHome = false
        }
    }

    val currentForecast: DailyForecast = generateDailyForecast(selectedDateButton, latestTaalDataHome)
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
                                text = "EyeTaal",
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
                                    )

                                    {
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
                                text = "Tomorrow",
                                isSelected = selectedDateButton == 0,
                                onClick = { selectedDateButton = 0 }
                            )
                            DateSelectionButton(
                                text = "2 days",
                                isSelected = selectedDateButton == 1,
                                onClick = { selectedDateButton = 1 }
                            )
                            DateSelectionButton(
                                text = "3 days",
                                isSelected = selectedDateButton == 2,
                                onClick = { selectedDateButton = 2 }
                            )
                        }

                        if (isLoadingHome) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            Text("Loading forecasts...", color = MuteTextDark)
                        } else if (errorHome != null) {
                            Text("Error loading forecasts: $errorHome", color = Color.Red, textAlign = TextAlign.Center)
                        } else {
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
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            1 -> {
                Box(modifier = Modifier.padding(paddingValues)) {
                    ActivityReportScreen(onBackClick = { selectedScreen = 0 })
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
fun generateDailyForecast(selectedDateButton: Int, latestData: TaalReportEntry?): DailyForecast {
    val alertLevel = latestData?.getAlertLevelInt() ?: 0

    return when (selectedDateButton) {
        0 -> {
            when (alertLevel) {
                0 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Low",
                    earthquakeDescription = "No significant seismic activity expected tomorrow.",
                    ashfallTitle = "Ashfall Forecast: Minimal",
                    ashfallDescription = "No ash emissions observed, minimal impact expected tomorrow.",
                    smogTitle = "Smog Forecast: Low",
                    smogDescription = "Good air quality expected tomorrow."
                )
                1 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Moderate",
                    earthquakeDescription = "Slight increase in seismic activity, minor tremors possible tomorrow.",
                    ashfallTitle = "Ashfall Forecast: Possible",
                    ashfallDescription = "Minor ash emissions possible tomorrow, sensitive individuals may be affected.",
                    smogTitle = "Smog Forecast: Moderate",
                    smogDescription = "Slight increase in volcanic gases tomorrow, sensitive individuals should take precautions."
                )
                2 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: High",
                    earthquakeDescription = "Increased seismic activity, potential for stronger earthquakes tomorrow.",
                    ashfallTitle = "Ashfall Forecast: Moderate",
                    ashfallDescription = "Light to moderate ashfall possible tomorrow, carry masks and limit outdoor exposure.",
                    smogTitle = "Smog Forecast: High",
                    smogDescription = "Elevated volcanic gas levels tomorrow, avoid outdoor activities. Use N95 masks."
                )
                3 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Very High",
                    earthquakeDescription = "Potential for destructive earthquakes and tremors tomorrow, prepare for evacuation.",
                    ashfallTitle = "Ashfall Forecast: Heavy",
                    ashfallDescription = "Heavy ashfall expected tomorrow, stay indoors, secure windows and doors.",
                    smogTitle = "Smog Forecast: Critical",
                    smogDescription = "Dangerous volcanic gas concentrations tomorrow, immediate evacuation may be required."
                )
                else -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Critical",
                    earthquakeDescription = "Imminent eruption possible tomorrow, expect very strong and frequent earthquakes. Follow evacuation orders.",
                    ashfallTitle = "Ashfall Forecast: Severe",
                    ashfallDescription = "Severe ashfall and pyroclastic flows possible tomorrow. Remain in designated safe zones.",
                    smogTitle = "Smog Forecast: Hazardous",
                    smogDescription = "Hazardous volcanic gases tomorrow. Follow all safety protocols and evacuation procedures."
                )
            }
        }
        1 -> {
            when (alertLevel) {
                0 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Low",
                    earthquakeDescription = "Seismic activity expected to remain low over the next 2 days.",
                    ashfallTitle = "Ashfall Forecast: Minimal",
                    ashfallDescription = "No significant ashfall expected over the next 2 days.",
                    smogTitle = "Smog Forecast: Low",
                    smogDescription = "Air quality expected to remain good over the next 2 days."
                )
                1 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Moderate",
                    earthquakeDescription = "Continued slight seismic activity, minor tremors possible over the next 2 days.",
                    ashfallTitle = "Ashfall Forecast: Possible",
                    ashfallDescription = "Continued minor ash emissions possible over the next 2 days, be prepared.",
                    smogTitle = "Smog Forecast: Moderate",
                    smogDescription = "Slightly elevated volcanic gases likely to persist over the next 2 days."
                )
                2 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: High",
                    earthquakeDescription = "Increased seismic activity likely to continue or intensify over the next 2 days.",
                    ashfallTitle = "Ashfall Forecast: Moderate to Heavy",
                    ashfallDescription = "Ashfall likely to continue over the next 2 days, may be heavier depending on activity.",
                    smogTitle = "Smog Forecast: High",
                    smogDescription = "High volcanic gas levels expected to persist over the next 2 days. Take precautions."
                )
                3 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Very High",
                    earthquakeDescription = "High risk of strong earthquakes and tremors over the next 2 days. Prepare for sustained unrest.",
                    ashfallTitle = "Ashfall Forecast: Heavy",
                    ashfallDescription = "Heavy ashfall likely to continue over the next 2 days. Monitor for changes.",
                    smogTitle = "Smog Forecast: Critical",
                    smogDescription = "Dangerous volcanic gas concentrations likely to persist over the next 2 days. Follow official advisories."
                )
                else -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Critical",
                    earthquakeDescription = "Eruption still possible over the next 2 days, expect continued strong earthquakes and tremors. Do not return to danger zones.",
                    ashfallTitle = "Ashfall Forecast: Severe",
                    ashfallDescription = "Severe ashfall and pyroclastic flows remain a threat over the next 2 days. Stay in safe areas.",
                    smogTitle = "Smog Forecast: Hazardous",
                    smogDescription = "Hazardous volcanic gases persist over the next 2 days. Continue to follow all safety and evacuation protocols."
                )
            }
        }
        2 -> {
            when (alertLevel) {
                0 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Low",
                    earthquakeDescription = "Seismic activity expected to remain stable and low over the next 3 days.",
                    ashfallTitle = "Ashfall Forecast: None",
                    ashfallDescription = "No significant ashfall expected in the next 3 days.",
                    smogTitle = "Smog Forecast: Low",
                    smogDescription = "Good air quality projected for the next 3 days, improving."
                )
                1 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Moderate",
                    earthquakeDescription = "Seismic activity expected to remain moderate with possible tremors over the next 3 days.",
                    ashfallTitle = "Ashfall Forecast: Possible Intermittent",
                    ashfallDescription = "Intermittent minor ash emissions possible over the next 3 days.",
                    smogTitle = "Smog Forecast: Moderate",
                    smogDescription = "Volcanic gases expected to remain at moderate levels over the next 3 days."
                )
                2 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: High",
                    earthquakeDescription = "High seismic activity likely to persist over the next 3 days, monitoring required.",
                    ashfallTitle = "Ashfall Forecast: Moderate to Heavy",
                    ashfallDescription = "Ashfall could be recurring, prepare for sustained conditions over the next 3 days.",
                    smogTitle = "Smog Forecast: High",
                    smogDescription = "Elevated volcanic gas levels expected to continue over the next 3 days."
                )
                3 -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Very High",
                    earthquakeDescription = "Sustained high risk of strong earthquakes and tremors over the next 3 days. Maintain high vigilance.",
                    ashfallTitle = "Ashfall Forecast: Heavy and Recurring",
                    ashfallDescription = "Expect heavy and recurring ashfall over the next 3 days. Secure your surroundings.",
                    smogTitle = "Smog Forecast: Critical",
                    smogDescription = "Dangerous volcanic gas levels are expected to persist over the next 3 days. Follow all safety and evacuation orders."
                )
                else -> DailyForecast(
                    earthquakeTitle = "Earthquake Forecast: Critical",
                    earthquakeDescription = "Imminent eruption state persists. Very high risk of major earthquakes. Stay vigilant and follow all official directives.",
                    ashfallTitle = "Ashfall Forecast: Severe and Prolonged",
                    ashfallDescription = "Severe and prolonged ashfall, pyroclastic flows are a continuous threat. Remain in safe zones.",
                    smogTitle = "Smog Forecast: Hazardous and Prolonged",
                    smogDescription = "Hazardous volcanic gases are expected to persist for days. Strict adherence to safety and evacuation protocols is paramount."
                )
            }
        }
        else -> DailyForecast(
            earthquakeTitle = "Forecast N/A",
            earthquakeDescription = "Unable to provide forecast for earthquakes.",
            ashfallTitle = "Forecast N/A",
            ashfallDescription = "Unable to provide forecast for ashfall.",
            smogTitle = "Forecast N/A",
            smogDescription = "Unable to provide forecast for smog."
        )
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
            icon = { Icon(Icons.Default.Warning, contentDescription = "Report") },
            label = { Text("Report", fontSize = 10.sp) },
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
    var allTaalReports by remember { mutableStateOf<List<TaalReportEntry>>(emptyList()) }
    var latestTaalData by remember { mutableStateOf<TaalReportEntry?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val taalApiService = remember {
        Retrofit.Builder()
            .baseUrl("https://eye-taal-webhost.vercel.app/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(TaalApiService::class.java)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val response = taalApiService.getLatestTaalData()
            if (response.success && response.data.isNotEmpty()) {
                val parsedReports = response.data.map { row ->
                    TaalReportEntry(
                        date = row.getOrNull(0) ?: "",
                        alertLevel = row.getOrNull(1) ?: "",
                        eruption = row.getOrNull(2) ?: "",
                        seismicity = row.getOrNull(3) ?: "",
                        acidity = row.getOrNull(4) ?: "",
                        temperature = row.getOrNull(5) ?: "",
                        sulfurDioxideFlux = row.getOrNull(6) ?: "",
                        plume = row.getOrNull(7) ?: "",
                        groundDeformation = row.getOrNull(8) ?: ""
                    )
                }
                allTaalReports = parsedReports
                latestTaalData = parsedReports.firstOrNull()

            } else {
                error = "API returned no data or was not successful."
            }
        } catch (e: Exception) {
            error = "Failed to load data: ${e.message}"
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
                        text = "Eye-Taal Activity Report",
                        color = MuteTextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            } else if (error != null) {
                Text("Error: $error", color = Color.Red, textAlign = TextAlign.Center)
            }
            else if (latestTaalData != null) {
                val data = latestTaalData!!

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
                            text = data.getAlertLevelInt()?.toString() ?: "N/A",
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
                    description = "${data.getVolcanicEarthquakesCount() ?: "N/A"} volcanic earthquakes including ${data.getVolcanicTremorsCount() ?: "N/A"} volcanic tremors (${data.getVolcanicTremorsDuration()})"
                )


                ReportDataCard(
                    iconResId = R.drawable.sodium_dioxide,
                    title = "Sulfur Dioxide Flux",
                    description = "${data.getSo2FluxValue() ?: "N/A"} tonnes/day (${data.getSo2FluxDate()})"
                )

                ReportDataCard(
                    iconResId = R.drawable.plume,
                    title = "Plume",
                    description = "${data.getPlumeHeightM() ?: "N/A"} meters tall; ${data.getPlumeEmissionStrength()} emission; ${data.getPlumeDriftDirection()} drift; exercise caution as visibility may be affected and mild ashfall is possible"
                )

                ReportDataCard(
                    iconResId = R.drawable.ground_deformation,
                    title = "Ground Deformation",
                    description = data.groundDeformation
                )

                ReportDataCard(
                    iconResId = R.drawable.temperature,
                    title = "Temperature",
                    description = "Main Crater Lake\n${data.getTemperatureValue()?.toString() ?: "N/A"} °C (${data.getTemperatureDate()})"
                )

                ReportDataCard(
                    iconResId = R.drawable.ph_scale,
                    title = "Acidity",
                    description = "Main Crater Lake\n${data.getAcidityValue()?.toString() ?: "N/A"} (${data.getAcidityDate()})"
                )

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text("No data available.", color = MuteTextDark)
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Taalapppt2Theme {
        TaalMonitoringApp()
    }
}