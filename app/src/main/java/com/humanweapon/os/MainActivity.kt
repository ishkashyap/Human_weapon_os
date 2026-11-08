package com.humanweapon.os

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingDateStr: String? = null

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    )

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            pendingDateStr?.let { syncDateFromHealthConnect(it) }
        } else {
            // Even if partial permissions granted, read what is permitted
            pendingDateStr?.let { syncDateFromHealthConnect(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidHealthBridge(), "AndroidHealthBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidHealthBridge {
        @JavascriptInterface
        fun requestSync(dateStr: String) {
            pendingDateStr = dateStr
            lifecycleScope.launch {
                val status = HealthConnectClient.getSdkStatus(this@MainActivity)
                if (status != HealthConnectClient.SDK_AVAILABLE) {
                    return@launch
                }
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    syncDateFromHealthConnect(dateStr)
                } else {
                    requestPermissionLauncher.launch(permissions)
                }
            }
        }
    }

    private fun syncDateFromHealthConnect(dateStr: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val date = LocalDate.parse(dateStr)
                val zone = ZoneId.systemDefault()
                val startOfDay = date.atStartOfDay(zone).toInstant()
                val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
                // Sleep can start the previous evening (from 19:00 previous day to 14:00 current day)
                val sleepWindowStart = date.minusDays(1).atTime(19, 0).atZone(zone).toInstant()
                val sleepWindowEnd = date.atTime(14, 0).atZone(zone).toInstant()

                val dayFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                val sleepFilter = TimeRangeFilter.between(sleepWindowStart, sleepWindowEnd)

                // 1. Steps from Samsung Health / Galaxy Watch 4 Classic
                val stepsRecords = client.readRecords(ReadRecordsRequest(StepsRecord::class, dayFilter)).records
                val totalSteps = stepsRecords.sumOf { it.count }

                // 2. Sleep duration (hours)
                val sleepRecords = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepFilter)).records
                val sleepMinutes = sleepRecords.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val sleepHours = (sleepMinutes / 60.0)

                // 3. Heart Rate (average bpm)
                val hrRecords = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, dayFilter)).records
                val allSamples = hrRecords.flatMap { it.samples }
                val avgHr = if (allSamples.isNotEmpty()) allSamples.map { it.beatsPerMinute }.average().toInt() else null

                // 4. Resting Heart Rate
                val rhrRecords = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, dayFilter)).records
                val restingHr = rhrRecords.lastOrNull()?.beatsPerMinute?.toInt()

                // 5. Active Calories Burned
                val calRecords = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, dayFilter)).records
                val activeKcal = calRecords.sumOf { it.energy.inKilocalories }.toInt()

                // 6. Exercise / Workout minutes
                val exRecords = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, dayFilter)).records
                val workoutMin = exRecords.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()

                // 7. SpO2 (Oxygen Saturation)
                val spo2Records = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, dayFilter)).records
                val spo2 = spo2Records.lastOrNull()?.percentage?.value?.toInt()

                val payload = JSONObject().apply {
                    put("date", dateStr)
                    put("steps", totalSteps)
                    put("sleepHrs", sleepHours)
                    if (avgHr != null) put("avgHr", avgHr)
                    if (restingHr != null) put("restingHr", restingHr)
                    put("activeKcal", activeKcal)
                    put("workoutMin", workoutMin)
                    if (spo2 != null) put("spo2", spo2)
                    put("device", "Galaxy Watch4 Classic (EEPD)")
                }

                withContext(Dispatchers.Main) {
                    val js = "window.onSamsungHealthData(" + JSONObject.quote(payload.toString()) + ");"
                    webView.evaluateJavascript(js, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
