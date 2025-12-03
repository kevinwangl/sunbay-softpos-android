package com.sunbay.softpos

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.sunbay.softpos.data.DeviceManager
import com.sunbay.softpos.data.ThreatReporter
import com.sunbay.softpos.data.TransactionTokenManager
import com.sunbay.softpos.ui.screens.DeviceScreen
import com.sunbay.softpos.ui.screens.SecurityScreen
import com.sunbay.softpos.ui.screens.TransactionScreen
import com.sunbay.softpos.ui.theme.SunbaySoftPOSTheme
import com.sunbay.softpos.ui.theme.SunmiBlack
import com.sunbay.softpos.ui.theme.SunmiOrange
import com.sunbay.softpos.utils.FileLogger
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化文件日志
        FileLogger.init(this)
        FileLogger.i("MainActivity", "Application started")
        FileLogger.i("MainActivity", "Log file: ${FileLogger.getCurrentLogPath()}")
        
        // 请求位置权限
        requestLocationPermissionIfNeeded()
        
        val deviceManager = DeviceManager(this)
        val threatReporter = ThreatReporter(this)
        val transactionTokenManager = TransactionTokenManager(this)
        
        // Load saved device ID if exists
        val savedDeviceId = deviceManager.getSavedDeviceId()
        if (savedDeviceId != null) {
            threatReporter.setDeviceId(savedDeviceId)
            Log.d("MainActivity", "Loaded saved device ID: $savedDeviceId")
        }
        
        setContent {
            SunbaySoftPOSTheme {
                MainScreen(deviceManager, threatReporter, transactionTokenManager)
            }
        }
    }
    
    /**
     * 请求位置权限（如果需要）
     */
    private fun requestLocationPermissionIfNeeded() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            
            Log.d("MainActivity", "请求位置权限")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d("MainActivity", "位置权限已授予")
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "位置权限已授予")
                    FileLogger.i("MainActivity", "位置权限已授予")
                } else {
                    Log.w("MainActivity", "位置权限被拒绝")
                    FileLogger.w("MainActivity", "位置权限被拒绝 - 交易将不包含位置信息")
                }
            }
        }
    }
}

// Shared state class for all tabs - Refactored to be reactive
class AppState {
    var status by mutableStateOf("Ready")
    var baseUrl by mutableStateOf("http://10.162.24.174:8180/")
    var detailedInfo by mutableStateOf("")
    var inputLogs by mutableStateOf("")
    var outputLogs by mutableStateOf("")
    var transactionAmount by mutableStateOf("10000")
    var cardNumber by mutableStateOf("6222021234567890")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    deviceManager: DeviceManager, 
    threatReporter: ThreatReporter,
    transactionTokenManager: TransactionTokenManager
) {
    var selectedTab by remember { mutableStateOf(0) }
    val appState = remember { AppState() }
    val scope = rememberCoroutineScope()
    var showUrlDialog by remember { mutableStateOf(false) }
    
    if (showUrlDialog) {
        var tempUrl by remember { mutableStateOf(appState.baseUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("设置服务器地址", fontSize = 12.sp) },
            text = {
                TextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("Base URL", fontSize = 12.sp) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    appState.baseUrl = tempUrl
                    showUrlDialog = false
                }) {
                    Text("确定", fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(onClick = { showUrlDialog = false }) {
                    Text("取消", fontSize = 12.sp)
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showUrlDialog = true }
                    ) {
                        Text(
                            text = appState.baseUrl,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit URL",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SunmiOrange,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "设备管理") },
                    label = { Text("设备管理") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Lock, contentDescription = "安全检测") },
                    label = { Text("安全检测") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = "交易处理") },
                    label = { Text("交易处理") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Left Side: Content Area (Operations)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (selectedTab) {
                    0 -> DeviceScreen(deviceManager, threatReporter, appState, scope)
                    1 -> SecurityScreen(threatReporter, appState, scope)
                    2 -> TransactionScreen(deviceManager, transactionTokenManager, appState, scope)
                }
            }
            
            // Right Side: Logs Section
            LogsSection(appState)
        }
    }
}

@Composable
fun LogsSection(appState: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .fillMaxHeight()
            .background(SunmiBlack)
            .padding(8.dp)
    ) {
        Text(
            text = "通信日志",
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Input Logs (Top)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "请求 ▶",
                fontSize = 12.sp,
                color = Color(0xFFFFAA00),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF222222), shape = RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Text(
                    text = appState.inputLogs.ifEmpty { "暂无请求..." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color(0xFFFFAA00)
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Output Logs (Bottom)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "◀ 响应",
                fontSize = 12.sp,
                color = Color(0xFF00FF00),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF222222), shape = RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Text(
                    text = appState.outputLogs.ifEmpty { "暂无响应..." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color(0xFF00FF00)
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
