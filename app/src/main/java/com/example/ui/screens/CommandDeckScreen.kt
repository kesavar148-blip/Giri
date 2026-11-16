package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.tools.JarvisToolsRepository
import com.example.data.tools.NewsArticle
import com.example.data.tools.SearchHit
import com.example.data.tools.WeatherReport
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudPanelElevated
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandDeckScreen(
    defaultCity: String,
    weatherReport: WeatherReport?,
    weatherLoading: Boolean,
    weatherError: String?,
    searchHits: List<SearchHit>,
    webSearchLoading: Boolean,
    newsArticles: List<NewsArticle>,
    newsLoading: Boolean,
    onRefreshWeatherCity: (String) -> Unit,
    onRefreshWeatherCoords: (Double, Double) -> Unit,
    onRunWebSearch: (String) -> Unit,
    onRefreshNews: (String) -> Unit,
    onEvaluateMath: (String) -> String,
    onSendToAssistant: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val toolsRepo = remember { JarvisToolsRepository() }

    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var cityInput by remember(defaultCity) { mutableStateOf(defaultCity) }
    var searchInput by remember { mutableStateOf("") }
    var mathInput by remember { mutableStateOf("sqrt(144) + 25 * 3") }
    var mathOutput by remember { mutableStateOf("87") }
    var toolFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy // z", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeStr = timeFmt.format(now)
            currentDateStr = dateFmt.format(now)
            delay(1000L)
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchGpsWeather() {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                onRefreshWeatherCoords(loc.latitude, loc.longitude)
            } else {
                toolFeedback = "Location unavailable on device; enter a city name instead."
            }
        }.addOnFailureListener {
            toolFeedback = "Unable to read location: ${it.localizedMessage}"
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            fetchGpsWeather()
        } else {
            toolFeedback = "Coarse location permission denied. Enter city manually."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Live Clock & System Telemetry
        item {
            HudSectionCard(title = "CHRONO & SYSTEM TELEMETRY", icon = Icons.Default.Schedule) {
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.displayLarge,
                    color = ArcCyan,
                )
                Text(
                    text = currentDateStr,
                    style = MaterialTheme.typography.labelLarge,
                    color = SteelMuted,
                )
            }
        }

        if (toolFeedback != null) {
            item {
                Text(
                    text = toolFeedback.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcCyan,
                )
            }
        }

        // 2. Live Open-Meteo Weather
        item {
            HudSectionCard(title = "ATMOSPHERIC WEATHER (OPEN-METEO FREE)", icon = Icons.Default.Cloud) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = hudTextFieldColors(),
                    )
                    Button(
                        onClick = { onRefreshWeatherCity(cityInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text("Check")
                    }
                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) fetchGpsWeather()
                            else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Use current location", tint = ArcCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (weatherLoading) {
                    CircularProgressIndicator(color = ArcCyan, modifier = Modifier.size(24.dp))
                } else if (weatherError != null) {
                    Text(weatherError, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                } else if (weatherReport != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${weatherReport.city}${if (weatherReport.country.isNotBlank()) ", ${weatherReport.country}" else ""}",
                                style = MaterialTheme.typography.titleMedium,
                                color = IceWhite,
                            )
                            Text(
                                text = weatherReport.conditionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArcCyan,
                            )
                            Text(
                                text = "H ${weatherReport.highC}°C / L ${weatherReport.lowC}°C • Humidity ${weatherReport.humidityPercent}% • Wind ${weatherReport.windKmh} km/h",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteelMuted,
                            )
                        }
                        Text(
                            text = "${weatherReport.temperatureC}°C",
                            style = MaterialTheme.typography.headlineLarge,
                            color = IceWhite,
                        )
                    }
                }
            }
        }

        // 3. Precision Calculator
        item {
            HudSectionCard(title = "MATHEMATICAL ENGINE", icon = Icons.Default.Calculate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = mathInput,
                        onValueChange = { mathInput = it },
                        label = { Text("Expression (e.g. (45*12)/sqrt(9))") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("math_input"),
                        colors = hudTextFieldColors(),
                    )
                    Button(
                        onClick = { mathOutput = onEvaluateMath(mathInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("math_eval_button"),
                    ) {
                        Text("Compute")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Result = $mathOutput",
                    style = MaterialTheme.typography.titleLarge,
                    color = NominalGreen,
                )
            }
        }

        // 4. Device & Website Launchers
        item {
            HudSectionCard(title = "DEVICE & APP LAUNCHERS", icon = Icons.Default.OpenInBrowser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            toolsRepo.openUrl(context, "https://www.wikipedia.org")
                                .onFailure { toolFeedback = it.localizedMessage }
                        },
                        border = BorderStroke(1.dp, HudBorderCyan),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browser", color = IceWhite)
                    }
                    OutlinedButton(
                        onClick = {
                            toolsRepo.openSetAlarm(context, 7, 30, "JARVIS Morning Briefing")
                                .onFailure { toolFeedback = "Clock alarm unavailable." }
                        },
                        border = BorderStroke(1.dp, HudBorderCyan),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Alarm", color = IceWhite)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            toolsRepo.openCalendarEventDraft(context, "JARVIS Scheduled Sync", "Created via JARVIS Command Deck")
                                .onFailure { toolFeedback = "Calendar app unavailable." }
                        },
                        border = BorderStroke(1.dp, HudBorderCyan),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Calendar", color = IceWhite)
                    }
                    OutlinedButton(
                        onClick = {
                            toolsRepo.openEmailDraft(context, "", "Draft from JARVIS", "")
                                .onFailure { toolFeedback = "Email client unavailable." }
                        },
                        border = BorderStroke(1.dp, HudBorderCyan),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compose Mail", color = IceWhite)
                    }
                }
            }
        }

        // 5. Real-Time Web Search (DuckDuckGo + Wikipedia)
        item {
            HudSectionCard(title = "LIVE WEB SEARCH (DUCKDUCKGO + WIKIPEDIA)", icon = Icons.Default.Search) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Search query") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = hudTextFieldColors(),
                    )
                    Button(
                        onClick = { onRunWebSearch(searchInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text("Search")
                    }
                }

                if (webSearchLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(color = ArcCyan, modifier = Modifier.size(24.dp))
                } else if (searchHits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchHits.forEach { hit ->
                            Surface(
                                color = HudPanelElevated,
                                border = BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.6f)),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        toolsRepo.openUrl(context, hit.url)
                                            .onFailure { toolFeedback = it.localizedMessage }
                                    },
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "[${hit.source}] ${hit.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ArcCyan,
                                    )
                                    Text(
                                        text = hit.snippet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = IceWhite,
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { onSendToAssistant("Summarize what we just searched about: $searchInput", true) },
                            border = BorderStroke(1.dp, ArcBlue),
                        ) {
                            Text("Summarize with JARVIS", color = ArcCyan)
                        }
                    }
                }
            }
        }

        // 6. Live News Headlines
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Newspaper, contentDescription = null, tint = ArcCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE WORLD NEWS FEED", style = MaterialTheme.typography.titleLarge, color = IceWhite)
                }
                IconButton(onClick = { onRefreshNews("") }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh news", tint = ArcCyan)
                }
            }
        }

        if (newsLoading) {
            item {
                CircularProgressIndicator(color = ArcCyan, modifier = Modifier.size(24.dp))
            }
        } else {
            items(newsArticles, key = { it.url }) { article ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
                    border = BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.7f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            toolsRepo.openUrl(context, article.url)
                                .onFailure { toolFeedback = it.localizedMessage }
                        },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${article.source} // ${article.publishedAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcCyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DeepHudSurface),
        border = BorderStroke(1.dp, HudBorderCyan),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ArcCyan,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun hudTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ArcCyan,
    unfocusedBorderColor = HudBorderCyan,
    focusedTextColor = IceWhite,
    unfocusedTextColor = IceWhite,
    focusedLabelColor = ArcCyan,
    unfocusedLabelColor = SteelMuted,
)
