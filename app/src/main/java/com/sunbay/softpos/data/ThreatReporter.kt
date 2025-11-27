package com.sunbay.softpos.data

import android.content.Context
import android.util.Log
import com.sunbay.softpos.network.BackendApi
import com.sunbay.softpos.network.NetworkModule
import com.sunbay.softpos.network.ThreatReportRequest
import com.sunbay.softpos.security.ThreatDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service for reporting threats to the backend
 */
class ThreatReporter(private val context: Context) {
    private val threatDetector = ThreatDetector(context)
    private val pendingThreats = ConcurrentLinkedQueue<ThreatReportRequest>()
    private var autoScanJob: Job? = null
    private var deviceId: String? = null

    companion object {
        private const val TAG = "ThreatReporter"
        private const val AUTO_SCAN_INTERVAL_MS = 300000L // 5 minutes
    }

    /**
     * Set the device ID for threat reporting
     */
    fun setDeviceId(id: String) {
        deviceId = id
        Log.d(TAG, "Device ID set: $id")
    }

    /**
     * Report a single threat to the backend
     */
    suspend fun reportThreat(
        baseUrl: String,
        threat: ThreatDetector.ThreatResult,
        onLog: ((ApiLog) -> Unit)? = null
    ): Result<String> {
        val currentDeviceId = deviceId ?: return Result.failure(
            Exception("Device ID not set. Please register device first.")
        )

        val request = ThreatReportRequest(
            deviceId = currentDeviceId,
            threatType = threat.threatType.name,
            severity = threat.severity.name,
            description = threat.description
        )

        return try {
            onLog?.invoke(
                ApiLog(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = "$baseUrl/api/v1/threats/report",
                    body = """
                        {
                          "deviceId": "${request.deviceId}",
                          "threatType": "${request.threatType}",
                          "severity": "${request.severity}",
                          "description": "${request.description}"
                        }
                    """.trimIndent()
                )
            )

                val api = NetworkModule.getApi(baseUrl)
            val response = api.reportThreat(request)

            if (response.isSuccessful) {
                val body = response.body()
                onLog?.invoke(
                    ApiLog(
                        timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        type = "RESPONSE",
                        method = "POST",
                        url = "$baseUrl/api/v1/threats/report",
                        statusCode = response.code(),
                        body = "Success: ${body?.message ?: "Threat reported"}"
                    )
                )
                Log.i(TAG, "Threat reported successfully: ${threat.threatType}")
                Result.success(body?.message ?: "Threat reported successfully")
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                onLog?.invoke(
                    ApiLog(
                        timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        type = "RESPONSE",
                        method = "POST",
                        url = "$baseUrl/api/v1/threats/report",
                        statusCode = response.code(),
                        body = "Error: $errorBody"
                    )
                )
                Log.e(TAG, "Failed to report threat: $errorBody")
                
                // Queue for retry
                pendingThreats.offer(request)
                
                Result.failure(Exception("Failed to report threat: $errorBody"))
            }
        } catch (e: Exception) {
            onLog?.invoke(
                ApiLog(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    type = "ERROR",
                    method = "POST",
                    url = "$baseUrl/api/v1/threats/report",
                    body = "Exception: ${e.message}"
                )
            )
            Log.e(TAG, "Exception reporting threat", e)
            
            // Queue for retry
            pendingThreats.offer(request)
            
            Result.failure(e)
        }
    }

    /**
     * Perform manual threat scan and report all detected threats
     */
    suspend fun scanAndReportThreats(
        baseUrl: String,
        onLog: ((ApiLog) -> Unit)? = null
    ): Result<List<String>> {
        val threats = threatDetector.getDetectedThreats()
        
        if (threats.isEmpty()) {
            onLog?.invoke(
                ApiLog(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    type = "INFO",
                    method = "SCAN",
                    url = "local://threat-scan",
                    body = "No threats detected"
                )
            )
            return Result.success(listOf("No threats detected"))
        }

        val results = mutableListOf<String>()
        for (threat in threats) {
            val result = reportThreat(baseUrl, threat, onLog)
            if (result.isSuccess) {
                results.add("${threat.threatType}: ${result.getOrNull()}")
            } else {
                results.add("${threat.threatType}: Failed - ${result.exceptionOrNull()?.message}")
            }
        }

        return Result.success(results)
    }

    /**
     * Start automatic threat scanning and reporting
     */
    fun startAutoScanning(
        baseUrl: String,
        scope: CoroutineScope,
        onLog: ((ApiLog) -> Unit)? = null
    ) {
        stopAutoScanning()

        autoScanJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Auto threat scanning started")
            onLog?.invoke(
                ApiLog(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    type = "INFO",
                    method = "SCAN",
                    url = "local://auto-scan",
                    body = "Automatic threat scanning started (interval: ${AUTO_SCAN_INTERVAL_MS / 1000}s)"
                )
            )

            while (isActive) {
                try {
                    // Perform scan
                    scanAndReportThreats(baseUrl, onLog)
                    
                    // Retry pending threats
                    retryPendingThreats(baseUrl, onLog)
                    
                    // Wait for next scan
                    delay(AUTO_SCAN_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto scan", e)
                    delay(AUTO_SCAN_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stop automatic threat scanning
     */
    fun stopAutoScanning() {
        autoScanJob?.cancel()
        autoScanJob = null
        Log.i(TAG, "Auto threat scanning stopped")
    }

    /**
     * Retry pending threats that failed to report
     */
    private suspend fun retryPendingThreats(
        baseUrl: String,
        onLog: ((ApiLog) -> Unit)? = null
    ) {
        val threatsToRetry = mutableListOf<ThreatReportRequest>()
        while (pendingThreats.isNotEmpty()) {
            pendingThreats.poll()?.let { threatsToRetry.add(it) }
        }

        if (threatsToRetry.isEmpty()) return

        Log.i(TAG, "Retrying ${threatsToRetry.size} pending threats")
        onLog?.invoke(
            ApiLog(
                timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                type = "INFO",
                method = "RETRY",
                url = "local://retry-queue",
                body = "Retrying ${threatsToRetry.size} pending threats"
            )
        )

        for (request in threatsToRetry) {
            try {
                    val api = NetworkModule.getApi(baseUrl)
                val response = api.reportThreat(request)
                
                if (!response.isSuccessful) {
                    // Re-queue if still failing
                    pendingThreats.offer(request)
                }
            } catch (e: Exception) {
                // Re-queue on exception
                pendingThreats.offer(request)
            }
        }
    }

    /**
     * Get current threat status
     */
    fun getThreatStatus(): String {
        val threats = threatDetector.getDetectedThreats()
        val highestThreat = threatDetector.getHighestSeverityThreat()
        
        return when {
            threats.isEmpty() -> "✓ No threats detected"
            highestThreat != null -> "⚠ ${threats.size} threat(s) detected - Highest: ${highestThreat.severity}"
            else -> "⚠ ${threats.size} threat(s) detected"
        }
    }

    /**
     * Get pending threats count
     */
    fun getPendingThreatsCount(): Int = pendingThreats.size
}
