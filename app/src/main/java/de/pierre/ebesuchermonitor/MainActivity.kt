package de.pierre.ebesuchermonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

private const val UI_BTP_EPSILON = 0.005

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EbesucherMonitorApp() }
    }
}

private enum class AppScreen { DASHBOARD, SETTINGS }

private val MonitorColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    secondary = Color(0xFF7FE7C4),
    background = Color(0xFF0A0F1C),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1A2333),
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EbesucherMonitorApp(viewModel: MonitorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }

    LaunchedEffect(state.autoRefresh, state.configured) {
        if (!state.autoRefresh || !state.configured) return@LaunchedEffect
        while (true) {
            delay(120_000L)
            viewModel.refresh(force = false)
        }
    }

    MaterialTheme(colorScheme = MonitorColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("eBesucher Monitor", fontWeight = FontWeight.Bold)
                            Text(
                                "v0.2.1 · Android",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        ConnectionPill(state.connected, state.loading)
                        IconButton(
                            onClick = { viewModel.refresh(force = true) },
                            enabled = state.configured && !state.loading
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Aktualisieren")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = screen == AppScreen.DASHBOARD,
                        onClick = { screen = AppScreen.DASHBOARD },
                        icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
                        label = { Text("Übersicht") }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.SETTINGS,
                        onClick = { screen = AppScreen.SETTINGS },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Einstellungen") }
                    )
                }
            }
        ) { padding ->
            when (screen) {
                AppScreen.DASHBOARD -> DashboardScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onOpenSettings = { screen = AppScreen.SETTINGS },
                    onRefresh = { viewModel.refresh(force = true) }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onSave = viewModel::saveCredentials,
                    onClear = viewModel::clearCredentials,
                    onAutoRefreshChanged = viewModel::setAutoRefresh
                )
            }
        }
    }
}

@Composable
private fun ConnectionPill(connected: Boolean, loading: Boolean) {
    val color = when {
        loading -> Color(0xFFF5B942)
        connected -> Color(0xFF3DDC84)
        else -> Color(0xFF7D8999)
    }
    val label = when {
        loading -> "PRÜFT"
        connected -> "API LIVE"
        else -> "OFFLINE"
    }

    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (!state.configured) {
            item { SetupCard(onOpenSettings) }
        } else {
            item { HeroCard(state) }

            item {
                val currentConfirmed = state.currentHourBtp > UI_BTP_EPSILON
                val previousConfirmed = state.previousHourBtp > UI_BTP_EPSILON
                val hourLabel = when {
                    currentConfirmed -> "Aktuelle Stunde"
                    previousConfirmed -> "Letzte bestätigte Stunde"
                    else -> "Stundenwert"
                }
                val hourValue = when {
                    currentConfirmed -> state.currentHourBtp
                    previousConfirmed -> state.previousHourBtp
                    else -> 0.0
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = hourLabel,
                        value = formatBtp(hourValue),
                        icon = { Icon(Icons.Rounded.Speed, null) }
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Surfbars mit Verdienst",
                        value = "${state.earningSurfbars} / ${state.surfbars.size}",
                        icon = { Icon(Icons.Rounded.Wifi, null) }
                    )
                }
            }

            item { EarningsChartCard(state.combinedHourlyBtp) }

            if (state.error != null) {
                item { ErrorCard(state.error, onRefresh) }
            }

            item {
                Text(
                    "SURFBARS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(state.surfbars, key = { it.id }) { surfbar -> SurfbarCard(surfbar) }
            item { ApiInfoCard(state) }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("API verbinden", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Trage deinen eBesucher-Benutzernamen und API-Key ein. Das normale eBesucher-Passwort wird nicht benötigt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Jetzt einrichten")
            }
        }
    }
}

@Composable
private fun HeroCard(state: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                "HEUTE LAUT API",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                formatBtp(state.totalTodayBtp),
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            if (state.currentHourBtp <= UI_BTP_EPSILON && state.previousHourBtp > UI_BTP_EPSILON) {
                Text(
                    "Aktuelle Stunde: noch keine bestätigten API-BTP",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF5B942)
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                if (state.lastUpdatedMillis > 0L) {
                    "Aktualisiert ${formatClock(state.lastUpdatedMillis)}"
                } else {
                    "Noch keine erfolgreiche Aktualisierung"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EarningsChartCard(values: List<Float>) {
    val chartColor = MaterialTheme.colorScheme.primary
    val maxValue = max(1f, values.maxOrNull() ?: 1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Verdienst heute", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Stündlich bestätigte API-Werte",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("24 h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val slot = size.width / values.size.coerceAtLeast(1)
                val barWidth = slot * 0.58f
                values.forEachIndexed { index, value ->
                    val height = if (value <= 0f) 2f else (value / maxValue) * size.height
                    drawRoundRect(
                        color = chartColor.copy(alpha = if (value > 0f) 0.95f else 0.18f),
                        topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00", "06", "12", "18", "24").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SurfbarCard(surfbar: SurfbarUiModel) {
    val statusColor = when (surfbar.status) {
        SurfStatus.EARNINGS_CONFIRMED -> Color(0xFF3DDC84)
        SurfStatus.PENDING_CONFIRMATION -> Color(0xFFF5B942)
        SurfStatus.NO_EARNINGS -> Color(0xFFFF6B6B)
    }
    val statusLabel = when (surfbar.status) {
        SurfStatus.EARNINGS_CONFIRMED -> "Verdienst bestätigt"
        SurfStatus.PENDING_CONFIRMATION -> "Noch unbestätigt"
        SurfStatus.NO_EARNINGS -> "Heute kein Verdienst"
    }
    val currentHourText = when {
        surfbar.currentHourBtp > UI_BTP_EPSILON -> formatBtp(surfbar.currentHourBtp)
        surfbar.status != SurfStatus.NO_EARNINGS -> "noch offen"
        else -> "0 BTP"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(surfbar.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        lastActivityText(surfbar.lastActivityMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    contentColor = statusColor,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth()) {
                SurfMetric("Heute", formatBtp(surfbar.todayBtp), Modifier.weight(1f))
                SurfMetric("Diese Stunde", currentHourText, Modifier.weight(1f))
                SurfMetric("Vorher", formatBtp(surfbar.previousHourBtp), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SurfMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ApiInfoCard(state: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("API-Status", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.rateLimitRemaining >= 0 && state.rateLimit.isNotBlank() ->
                        "Verbleibend: ${state.rateLimitRemaining} / ${state.rateLimit} Requests"
                    state.connected -> "Verbunden · Rate-Limit-Header nicht verfügbar"
                    else -> "Nicht verbunden"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Status bedeutet bestätigten Tagesverdienst, nicht garantiert eine gerade laufende Surfbar. lastActivity ist nur ein zusätzlicher Hinweis und kann zeitversetzt sein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B6B).copy(alpha = 0.10f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WifiOff, null, tint = Color(0xFFFF6B6B))
            Spacer(Modifier.width(10.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onRefresh) { Text("Nochmal") }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit,
    onAutoRefreshChanged: (Boolean) -> Unit
) {
    var username by remember(state.username) { mutableStateOf(state.username) }
    var apiKey by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("API-Zugang", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Der API-Key wird mit einem Schlüssel aus dem Android Keystore verschlüsselt gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("eBesucher Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (state.configured) "API-Key · leer = behalten" else "API-Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        onSave(username, apiKey)
                        apiKey = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading
                ) {
                    Text(if (state.configured) "Zugang aktualisieren" else "Speichern & verbinden")
                }

                if (state.configured) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text("Gespeicherten API-Zugang löschen")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Automatisch aktualisieren", fontWeight = FontWeight.Bold)
                    Text(
                        "Alle 2 Minuten, solange die App geöffnet ist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.autoRefresh, onCheckedChange = onAutoRefreshChanged)
            }
        }

        if (state.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B6B).copy(alpha = 0.10f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(state.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun formatBtp(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    return "${formatter.format(value)} BTP"
}

private fun formatClock(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.GERMANY).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }.format(Date(timestamp))
}

private fun lastActivityText(timestamp: Long): String {
    if (timestamp <= 0L) return "API lastActivity: keine Angabe"
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    return when {
        minutes < 1L -> "Letzte API-Aktivität: gerade eben"
        minutes == 1L -> "Letzte API-Aktivität: vor 1 Minute"
        minutes < 60L -> "Letzte API-Aktivität: vor $minutes Minuten"
        else -> "Letzte API-Aktivität: ${formatClock(timestamp)}"
    }
}
