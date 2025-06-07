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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

import com.example.taalapppt2.ui.theme.Taalapppt2Theme


val MutePrimary = Color(0xFF555555)
val MuteLightBackground = Color(0xFFF0F0F0)
val MuteMidBackground = Color(0xFFE0E0E0)
val MuteCardBackground = Color(0xFFFFFFFF)
val MuteTextDark = Color.Black
val MuteTextLight = Color.White



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

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("MMMM dd, HH:mm")
        val philippineZone = ZoneId.of("Asia/Manila")
        currentDateTimeString = LocalDateTime.now(philippineZone).format(formatter)
    }

    val density = LocalDensity.current

    val imageContentSectionHeight = 350.dp
    val titleSectionHeight = with(density) {
        (24.sp.toDp() + 16.dp + 8.dp)
    }

    Scaffold(
        topBar = { /* Nothing here */ },
        bottomBar = {
            TaalMonitoringBottomBar()
        },
        containerColor = MuteLightBackground, // Overall screen background
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Top section with background image and title
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
                        color = MuteTextLight, // Text color on dark image
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
                    predictionTitle = "4% Earthquake Prediction",
                    description = "Minor tremors recorded, no significant threat"
                )
                PredictionCard(
                    iconResId = R.drawable.ashfall,
                    predictionTitle = "1% Ashfall Prediction",
                    description = "Minor ash emissions observed, minimal impact expected"
                )
                PredictionCard(
                    iconResId = R.drawable.smog,
                    predictionTitle = "7% Smog Prediction",
                    description = "Slight increase in pollutants, sensitive individuals should take precautions"
                )

                Spacer(modifier = Modifier.height(16.dp))
                // Removed the 200.dp Spacer to decrease blank space at the bottom
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
                    tint = MuteTextDark, // Icon tint
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(description, fontSize = 14.sp, color = MuteTextDark)
        }
    }
}

@Composable
fun TaalMonitoringBottomBar() {
    var selectedItem by remember { mutableIntStateOf(0) }

    NavigationBar(
        containerColor = MuteCardBackground,
        modifier = Modifier.height(60.dp)
    ) {
        NavigationBarItem(
            selected = selectedItem == 0,
            onClick = { selectedItem = 0 },
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
            onClick = { selectedItem = 1 },
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
            onClick = { selectedItem = 2 },
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Taalapppt2Theme {
        TaalMonitoringApp()
    }
}