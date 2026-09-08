package de.pierre.ebesuchermonitor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val BERLIN_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Europe/Berlin")

enum class SurfStatus {
    EARNINGS_CONFIRMED,
    PENDING_CONFIRMATION,
    NO_EARNINGS
}

data class SurfbarUiModel(
    val id: Long,
    val apiName: String,
    val alias: String,
    val url: String,
    val lastActivityMillis: Long,
    val todayBtp: Double,
    val currentHourBtp: Double,
    val previousHourBtp: Double,
    val hourlyBtp: List<Float>,
    val status: SurfStatus
) {
    val displayName: String
        get() = alias.ifBlank { apiName }
}

data class DailyEarningsUiModel(
    val dayStartMillis: Long,
    val totalBtp: Double?
)

data class DashboardUiState(
    val configured: Boolean = false,
    val username: String = "",
    val loading: Boolean = false,
    val connected: Boolean = false,
    val autoRefresh: Boolean = true,
    val totalTodayBtp: Double = 0.0,
    val deltaSinceLastRefresh: Double = 0.0,
    val currentHourBtp: Double = 0.0,
    val previousHourBtp: Double = 0.0,
    val lastConfirmedHourBtp: Double = 0.0,
    val lastConfirmedApiHour: Int = 0,
    val forecastTodayBtp: Double = 0.0,
    val averageEarningHourBtp: Double = 0.0,
    val bestHourBtp: Double = 0.0,
    val bestApiHour: Int = 0,
    val earningSurfbars: Int = 0,
    val surfbars: List<SurfbarUiModel> = emptyList(),
    val combinedHourlyBtp: List<Float> = List(24) { 0f },
    val recentDays: List<DailyEarningsUiModel> = emptyList(),
    val rateLimitRemaining: Int = -1,
    val rateLimit: String = "",
    val lastUpdatedMillis: Long = 0L,
    val nextRefreshAllowedMillis: Long = 0L,
    val error: String? = null
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = SecureCredentialStore(application)
    private val snapshotDatabase = SnapshotDatabase(application)
    private val settings = application.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private val initialCredentials = credentialStore.load()
    private val _uiState = MutableStateFlow(
        DashboardUiState(
            configured = initialCredentials != null,
            username = initialCredentials?.first.orEmpty(),
            autoRefresh = settings.getBoolean(KEY_AUTO_REFRESH, true)
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        if (initialCredentials != null) {
            refresh(force = false)
        }
    }

    fun saveCredentials(username: String, apiKey: String) {
        val cleanUsername = username.trim()
        val existingKey = credentialStore.load()?.second.orEmpty()
        val finalApiKey = if (apiKey.isBlank()) existingKey else apiKey.trim()

        if (cleanUsername.isBlank() || finalApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Benutzername und API-Key werden benötigt."
            )
            return
        }

        credentialStore.save(cleanUsername, finalApiKey)
        _uiState.value = _uiState.value.copy(
            configured = true,
            username = cleanUsername,
            nextRefreshAllowedMillis = 0L,
            error = null
        )
        refresh(force = true)
    }

    fun clearCredentials() {
        credentialStore.clear()
        _uiState.value = DashboardUiState(
            autoRefresh = _uiState.value.autoRefresh
        )
    }

    fun setAutoRefresh(enabled: Boolean) {
        settings.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
        _uiState.value = _uiState.value.copy(autoRefresh = enabled)
    }

    fun setSurfbarAlias(id: Long, alias: String) {
        val cleanAlias = alias.trim()
        val key = aliasKey(id)
        if (cleanAlias.isBlank()) {
            settings.edit().remove(key).apply()
        } else {
            settings.edit().putString(key, cleanAlias).apply()
        }

        _uiState.value = _uiState.value.copy(
            surfbars = _uiState.value.surfbars.map { surfbar ->
                if (surfbar.id == id) surfbar.copy(alias = cleanAlias) else surfbar
            }
        )
    }

    fun refresh(force: Boolean = true) {
        if (_uiState.value.loading) return

        val credentials = credentialStore.load()
        if (credentials == null) {
            _uiState.value = _uiState.value.copy(
                configured = false,
                connected = false,
                error = "Bitte zuerst die eBesucher-API einrichten."
            )
            return
        }

        val now = System.currentTimeMillis()
        val nextSafeRefresh = _uiState.value.nextRefreshAllowedMillis
        if (nextSafeRefresh > now) {
            if (force) {
                _uiState.value = _uiState.value.copy(
                    error = "API-Schutz aktiv: Nächste vollständige Aktualisierung in ${formatWait(nextSafeRefresh - now)}."
                )
            }
            return
        }

        val waitSeconds = EbesucherApi.secondsUntilFullRefreshAvailable()
        if (waitSeconds > 0L) {
            if (force) {
                _uiState.value = _uiState.value.copy(
                    error = "Lokaler API-Schutz: Noch etwa $waitSeconds Sekunden warten."
                )
            }
            return
        }

        _uiState.value = _uiState.value.copy(loading = true, error = null)

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    loadDashboard(credentials.first, credentials.second)
                }
                _uiState.value = result.copy(
                    autoRefresh = _uiState.value.autoRefresh,
                    username = credentials.first,
                    configured = true
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    connected = false,
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    private fun loadDashboard(username: String, apiKey: String): DashboardUiState {
        val api = EbesucherApi(username, apiKey)
        val rawLinks = api.surflinks
            .sortedByDescending { it.lastActivityMillis }
            .take(MAX_SURFBARS)

        val calendar = Calendar.getInstance(BERLIN_TIME_ZONE, Locale.GERMANY)
        val currentApiHour = (calendar.get(Calendar.HOUR_OF_DAY) + 1).coerceIn(1, 24)
        val completedApiHours = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 24)
        val previousApiHour = currentApiHour - 1
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply {
            timeZone = BERLIN_TIME_ZONE
        }.format(Date())

        val combinedHours = MutableList(24) { 0f }
        val surfbarModels = rawLinks.map { link ->
            val hourly = api.getHourlyEarningsBreakdown(link.fullName, today)
            val values = (1..24).map { hour -> (hourly[hour] ?: 0.0).toFloat() }
            values.forEachIndexed { index, value ->
                combinedHours[index] += value
            }

            val current = hourly[currentApiHour] ?: 0.0
            val previous = if (previousApiHour >= 1) hourly[previousApiHour] ?: 0.0 else 0.0
            val total = hourly.values.sum()
            val apiName = link.fullName.ifBlank { "Unbenannter Surflink" }

            SurfbarUiModel(
                id = link.id,
                apiName = apiName,
                alias = settings.getString(aliasKey(link.id), "").orEmpty(),
                url = link.url,
                lastActivityMillis = link.lastActivityMillis,
                todayBtp = total,
                currentHourBtp = current,
                previousHourBtp = previous,
                hourlyBtp = values,
                status = calculateStatus(total, link.lastActivityMillis)
            )
        }

        val totalToday = surfbarModels.sumOf { it.todayBtp }
        val previousSnapshot = snapshotDatabase.latestTotalForToday()
        val deltaSinceLastRefresh = previousSnapshot?.let { totalToday - it } ?: 0.0
        val currentHour = surfbarModels.sumOf { it.currentHourBtp }
        val previousHour = surfbarModels.sumOf { it.previousHourBtp }
        val earningCount = surfbarModels.count { it.todayBtp > BTP_EPSILON }

        val completedValues = combinedHours.take(completedApiHours)
        val completedTotal = completedValues.sumOf { it.toDouble() }
        val elapsedForecast = if (completedApiHours > 0) {
            completedTotal / completedApiHours.toDouble() * 24.0
        } else {
            totalToday
        }
        val forecast = maxOf(totalToday, elapsedForecast)

        val positiveCompleted = completedValues.filter { it > BTP_EPSILON.toFloat() }
        val averageEarningHour = if (positiveCompleted.isNotEmpty()) {
            positiveCompleted.map { it.toDouble() }.average()
        } else {
            0.0
        }

        val bestCompletedIndex = completedValues.withIndex()
            .maxByOrNull { it.value }
            ?.takeIf { it.value > BTP_EPSILON.toFloat() }
            ?.index ?: -1
        val bestHour = if (bestCompletedIndex >= 0) completedValues[bestCompletedIndex].toDouble() else 0.0
        val bestApiHour = if (bestCompletedIndex >= 0) bestCompletedIndex + 1 else 0

        val visibleHours = combinedHours.take(currentApiHour)
        val lastConfirmedIndex = visibleHours.indexOfLast { it > BTP_EPSILON.toFloat() }
        val lastConfirmedHour = if (lastConfirmedIndex >= 0) visibleHours[lastConfirmedIndex].toDouble() else 0.0
        val lastConfirmedApiHour = if (lastConfirmedIndex >= 0) lastConfirmedIndex + 1 else 0

        snapshotDatabase.insertSnapshot(
            totalBtp = totalToday,
            activeSurfbars = earningCount,
            surfbarCount = surfbarModels.size
        )

        val recentDays = snapshotDatabase.getDailyMaxEarnings(7).map {
            DailyEarningsUiModel(it.dayStartMillis, it.totalBtp)
        }

        val requestCount = 1 + rawLinks.size
        val refreshInterval = calculateSafeRefreshInterval(api.rateLimit.orEmpty(), requestCount)
        val updatedAt = System.currentTimeMillis()

        return DashboardUiState(
            configured = true,
            username = username,
            loading = false,
            connected = true,
            totalTodayBtp = totalToday,
            deltaSinceLastRefresh = deltaSinceLastRefresh,
            currentHourBtp = currentHour,
            previousHourBtp = previousHour,
            lastConfirmedHourBtp = lastConfirmedHour,
            lastConfirmedApiHour = lastConfirmedApiHour,
            forecastTodayBtp = forecast,
            averageEarningHourBtp = averageEarningHour,
            bestHourBtp = bestHour,
            bestApiHour = bestApiHour,
            earningSurfbars = earningCount,
            surfbars = surfbarModels,
            combinedHourlyBtp = combinedHours,
            recentDays = recentDays,
            rateLimitRemaining = api.rateLimitRemaining,
            rateLimit = api.rateLimit.orEmpty(),
            lastUpdatedMillis = updatedAt,
            nextRefreshAllowedMillis = updatedAt + refreshInterval,
            error = null
        )
    }

    private fun calculateStatus(todayBtp: Double, lastActivityMillis: Long): SurfStatus {
        if (todayBtp > BTP_EPSILON) return SurfStatus.EARNINGS_CONFIRMED

        if (lastActivityMillis > 0L) {
            val age = System.currentTimeMillis() - lastActivityMillis
            if (age in 0..PENDING_WINDOW_MS) {
                return SurfStatus.PENDING_CONFIRMATION
            }
        }

        return SurfStatus.NO_EARNINGS
    }

    private fun calculateSafeRefreshInterval(limitHeader: String, requestCount: Int): Long {
        val limit = Regex("\\d+").find(limitHeader)?.value?.toIntOrNull()
            ?: return DEFAULT_AUTO_REFRESH_MS
        if (limit <= 0) return DEFAULT_AUTO_REFRESH_MS

        val refreshesPerHour = (limit / requestCount.coerceAtLeast(1)).coerceAtLeast(1)
        return (ONE_HOUR_MS / refreshesPerHour).coerceAtLeast(MIN_AUTO_REFRESH_MS)
    }

    private fun formatWait(waitMillis: Long): String {
        val totalSeconds = ((waitMillis + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "$minutes Min ${seconds}s" else "$seconds s"
    }

    private fun aliasKey(id: Long): String = "surfbar_alias_$id"

    companion object {
        private const val SETTINGS_PREFS = "ebesucher_monitor_settings_v2"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
        private const val MAX_SURFBARS = 4
        private const val BTP_EPSILON = 0.005
        private const val PENDING_WINDOW_MS = 60L * 60L * 1000L
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
        private const val MIN_AUTO_REFRESH_MS = 2L * 60L * 1000L
        private const val DEFAULT_AUTO_REFRESH_MS = 15L * 60L * 1000L
    }
}
