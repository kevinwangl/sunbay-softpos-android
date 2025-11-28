package com.sunbay.softpos.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sunbay.softpos.AppState
import com.sunbay.softpos.data.DeviceManager
import com.sunbay.softpos.data.ThreatReporter
import com.sunbay.softpos.ui.components.SunbayButton
import com.sunbay.softpos.ui.components.SunbayCard
import com.sunbay.softpos.ui.components.SunbaySelectionDialog
import com.sunbay.softpos.ui.components.SunbaySelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DeviceScreen(
    deviceManager: DeviceManager,
    threatReporter: ThreatReporter,
    appState: AppState,
    scope: CoroutineScope
) {
    // Define operations
    val operations = listOf(
        "健康检查",
        "注册设备",
        "签到 (Sign In)",
        "签退 (Sign Out)",
        "密钥注入 (Key Injection)",
        "PinPad认证 (Attest)"
    )

    var selectedOpIndex by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        SunbaySelectionDialog(
            title = "选择操作",
            options = operations,
            onDismiss = { showDialog = false },
            onOptionSelected = { index ->
                selectedOpIndex = index
                showDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        SunbaySelector(
                selectedText = operations[selectedOpIndex],
            onClick = { showDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SunbayButton(
            text = "确定",
            onClick = {
                    when (selectedOpIndex) {
                        0 -> { // 健康检查
                            scope.launch {
                                appState.status = "健康检查中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.healthCheck(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ 健康检查成功" else "✗ 健康检查失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        1 -> { // 注册设备
                            scope.launch {
                                appState.status = "设备注册中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.registerDevice(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) {
                                    val responseText = result.getOrNull() ?: ""
                                    val deviceIdRegex = "\"device_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                                    val deviceId = deviceIdRegex.find(responseText)?.groupValues?.get(1)
                                    if (deviceId != null) {
                                        threatReporter.setDeviceId(deviceId)
                                    }
                                    "✓ 设备注册成功"
                                } else {
                                    "✗ 设备注册失败"
                                }
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        2 -> { // 签到
                            scope.launch {
                                appState.status = "签到中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.login(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ 签到成功" else "✗ 签到失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        3 -> { // 签退
                            scope.launch {
                                appState.status = "签退中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.logout(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ 签退成功" else "✗ 签退失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        4 -> { // 密钥注入
                            scope.launch {
                                appState.status = "密钥注入中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.injectKey(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ 密钥注入成功" else "✗ 密钥注入失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        5 -> { // PinPad认证
                            scope.launch {
                                appState.status = "PinPad认证中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = deviceManager.attestPinpad(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ PinPad认证成功" else "✗ PinPad认证失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                    }
                }
            )
    }
}
