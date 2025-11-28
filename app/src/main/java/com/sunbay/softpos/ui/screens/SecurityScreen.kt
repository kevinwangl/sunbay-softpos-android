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
import com.sunbay.softpos.data.ThreatReporter
import com.sunbay.softpos.ui.components.SunbayButton
import com.sunbay.softpos.ui.components.SunbayCard
import com.sunbay.softpos.ui.components.SunbaySelectionDialog
import com.sunbay.softpos.ui.components.SunbaySelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    threatReporter: ThreatReporter,
    appState: AppState,
    scope: CoroutineScope
) {
    val operations = listOf(
        "扫描威胁",
        "查看威胁状态"
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
                        0 -> { // 扫描威胁
                            scope.launch {
                                appState.status = "扫描威胁中..."
                                appState.inputLogs = ""
                                appState.outputLogs = ""
                                val result = threatReporter.scanAndReportThreats(appState.baseUrl) { log ->
                                    if (log.type == "REQUEST") {
                                        appState.inputLogs += log.toDisplayString()
                                    } else {
                                        appState.outputLogs += log.toDisplayString()
                                    }
                                }
                                appState.status = if (result.isSuccess) "✓ 威胁扫描完成" else "✗ 威胁扫描失败"
                                appState.detailedInfo = result.getOrNull()?.joinToString("\n") ?: result.exceptionOrNull()?.message ?: "Unknown Error"
                            }
                        }
                        1 -> { // 查看威胁状态
                            val threatStatus = threatReporter.getThreatStatus()
                            val pendingCount = threatReporter.getPendingThreatsCount()
                            appState.status = "威胁状态"
                            appState.detailedInfo = "$threatStatus\n待上报威胁数: $pendingCount"
                        }
                    }
                }
            )
    }
}
