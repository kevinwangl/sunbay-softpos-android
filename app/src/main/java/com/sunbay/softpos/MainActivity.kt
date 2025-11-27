package com.sunbay.softpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunbay.softpos.data.ApiLog
import com.sunbay.softpos.data.DeviceManager
import com.sunbay.softpos.data.ThreatReporter
import com.sunbay.softpos.ui.theme.SunbaySoftPOSTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceManager = DeviceManager(this)
        val threatReporter = ThreatReporter(this)
        
        // Load saved device ID if exists
        val savedDeviceId = deviceManager.getSavedDeviceId()
        if (savedDeviceId != null) {
            threatReporter.setDeviceId(savedDeviceId)
            Log.d("MainActivity", "Loaded saved device ID: $savedDeviceId")
        }
        
        setContent {
            SunbaySoftPOSTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RegistrationScreen(deviceManager, threatReporter)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(deviceManager: DeviceManager, threatReporter: ThreatReporter) {
    var status by remember { mutableStateOf("Ready") }
    var baseUrl by remember { mutableStateOf("http://10.23.10.54:8080/") }
    var detailedInfo by remember { mutableStateOf("") }
    var inputLogs by remember { mutableStateOf("") }
    var outputLogs by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Sunbay SoftPOS Demo",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Backend URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Checking health..."
                        inputLogs = ""
                        outputLogs = ""
                        val result = deviceManager.healthCheck(baseUrl) { log ->
                            if (log.type == "REQUEST") {
                                inputLogs += log.toDisplayString()
                            } else {
                                outputLogs += log.toDisplayString()
                            }
                        }
                        status = if (result.isSuccess) "Health Check OK" else "Health Check Failed"
                        detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Health Check")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        status = "Registering..."
                        inputLogs = ""
                        outputLogs = ""
                        val result = deviceManager.registerDevice(baseUrl) { log ->
                            if (log.type == "REQUEST") {
                                inputLogs += log.toDisplayString()
                            } else {
                                outputLogs += log.toDisplayString()
                            }
                        }
                        status = if (result.isSuccess) {
                            // Extract device ID from response and set it for threat reporter
                            val responseText = result.getOrNull() ?: ""
                            // Try to extract device_id from response
                            val deviceIdRegex = "\"device_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                            val deviceId = deviceIdRegex.find(responseText)?.groupValues?.get(1)
                            if (deviceId != null) {
                                threatReporter.setDeviceId(deviceId)
                            }
                            "Registration Successful"
                        } else {
                            "Registration Failed"
                        }
                        detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Register Device")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Threat Scanning Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        status = "Scanning for threats..."
                        inputLogs = ""
                        outputLogs = ""
                        val result = threatReporter.scanAndReportThreats(baseUrl) { log ->
                            if (log.type == "REQUEST") {
                                inputLogs += log.toDisplayString()
                            } else {
                                outputLogs += log.toDisplayString()
                            }
                        }
                        status = if (result.isSuccess) "Threat Scan Complete" else "Threat Scan Failed"
                        detailedInfo = result.getOrNull()?.joinToString("\n") ?: result.exceptionOrNull()?.message ?: "Unknown Error"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Scan Threats")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = {
                    val threatStatus = threatReporter.getThreatStatus()
                    val pendingCount = threatReporter.getPendingThreatsCount()
                    status = "Threat Status"
                    detailedInfo = "$threatStatus\nPending reports: $pendingCount"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Threat Status")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status: $status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (detailedInfo.isNotEmpty()) {
                    Text(
                        text = detailedInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.contains("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Input Logs Section
        Text(
            text = "Log Input",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF2B2B2B), shape = RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = inputLogs.ifEmpty { "No input logs..." },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFFFAA00) // Orange color for input logs
                )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Output Logs Section
        Text(
            text = "Log Output",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF2B2B2B), shape = RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = outputLogs.ifEmpty { "No output logs..." },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF00FF00) // Green color for output logs
                )
            )
        }
    }
}
