package com.sunbay.softpos.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sunbay.softpos.AppState
import com.sunbay.softpos.data.DeviceManager
import com.sunbay.softpos.data.TransactionTokenManager
import com.sunbay.softpos.ui.components.SunbayButton
import com.sunbay.softpos.ui.components.SunbayCard
import com.sunbay.softpos.ui.components.SunbaySelectionDialog
import com.sunbay.softpos.ui.components.SunbaySelector
import com.sunbay.softpos.ui.components.SunbayTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun TransactionScreen(
    deviceManager: DeviceManager,
    transactionTokenManager: TransactionTokenManager,
    appState: AppState,
    scope: CoroutineScope
) {
    val operations = listOf(
        "1. 交易鉴证",
        "2. 交易处理"
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
        SunbayTextField(
            value = appState.transactionAmount,
            onValueChange = { appState.transactionAmount = it },
            label = "金额(分)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
            
        SunbayTextField(
            value = appState.cardNumber,
            onValueChange = { appState.cardNumber = it },
            label = "卡号",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SunbaySelector(
                selectedText = operations[selectedOpIndex],
            onClick = { showDialog = true }
        )

            Spacer(modifier = Modifier.height(8.dp))

        SunbayButton(
            text = "确定",
            onClick = {
                    when (selectedOpIndex) {
                        0 -> { // 交易鉴证
                            scope.launch {
                                val deviceId = deviceManager.getSavedDeviceId()
                                if (deviceId == null) {
                                    appState.status = "✗ 错误"
                                    appState.detailedInfo = "请先在设备管理页面注册设备"
                                    return@launch
                                }
                                
                                appState.status = "交易鉴证中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                
                                val amount = appState.transactionAmount.toLongOrNull() ?: 10000L
                                val result = transactionTokenManager.attestTransaction(
                                    appState.baseUrl, deviceId, amount, "CNY"
                                ) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                
                                appState.status = if (result.isSuccess) "✓ 交易鉴证成功" else "✗ 交易鉴证失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                        1 -> { // 交易处理
                            scope.launch {
                                appState.status = "交易处理中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                
                                val amount = appState.transactionAmount.toLongOrNull() ?: 10000L
                                val result = transactionTokenManager.processTransaction(
                                    appState.baseUrl, appState.cardNumber, amount, "CNY"
                                ) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                
                                appState.status = if (result.isSuccess) "✓ 交易处理成功" else "✗ 交易处理失败"
                                appState.detailedInfo = result.getOrElse { it.message ?: "Unknown Error" }
                            }
                        }
                    }
                }
            )
    }
}
