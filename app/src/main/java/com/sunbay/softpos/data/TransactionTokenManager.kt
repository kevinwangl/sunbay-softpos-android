package com.sunbay.softpos.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sunbay.softpos.network.NetworkModule
import com.sunbay.softpos.network.TransactionAttestRequest
import com.sunbay.softpos.network.ProcessTransactionRequest
import com.sunbay.softpos.security.ThreatDetector
import com.sunbay.softpos.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 交易令牌管理器
 * 负责交易鉴证和交易处理的演示
 */
class TransactionTokenManager(private val context: Context) {
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val prefs = context.getSharedPreferences("transaction_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_TRANSACTION_TOKEN = "transaction_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val TAG = "TransactionTokenManager"
    }
    
    /**
     * 保存交易令牌
     */
    private fun saveTransactionToken(token: String, expiresAt: String) {
        prefs.edit().apply {
            putString(KEY_TRANSACTION_TOKEN, token)
            putString(KEY_TOKEN_EXPIRES_AT, expiresAt)
            apply()
        }
        Log.d(TAG, "Saved transaction token, expires at: $expiresAt")
    }
    
    /**
     * 获取保存的交易令牌
     */
    fun getSavedTransactionToken(): String? {
        return prefs.getString(KEY_TRANSACTION_TOKEN, null)
    }
    
    /**
     * 获取令牌过期时间
     */
    fun getTokenExpiresAt(): String? {
        return prefs.getString(KEY_TOKEN_EXPIRES_AT, null)
    }
    
    /**
     * 清除交易令牌
     */
    fun clearTransactionToken() {
        prefs.edit().apply {
            remove(KEY_TRANSACTION_TOKEN)
            remove(KEY_TOKEN_EXPIRES_AT)
            apply()
        }
        Log.d(TAG, "Cleared transaction token")
    }
    
    /**
     * 步骤1：交易鉴证 - 获取交易令牌
     * 
     * 这个步骤会：
     * 1. 收集设备健康状态
     * 2. 发送到后端进行鉴证
     * 3. 获取交易令牌
     */
    suspend fun attestTransaction(
        baseUrl: String,
        deviceId: String,
        amount: Long,
        currency: String = "CNY",
        onLog: (ApiLog) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                FileLogger.i(TAG, "========== 开始交易鉴证 ==========")
                FileLogger.i(TAG, "设备ID: $deviceId, 金额: $amount, 币种: $currency")
                
                // 构建鉴证请求（后端不需要 health_check 字段）
                val request = TransactionAttestRequest(
                    device_id = deviceId,
                    amount = amount,
                    currency = currency
                )
                
                val url = "${baseUrl}api/v1/transactions/attest"
                val requestBody = gson.toJson(request)
                
                // 记录请求日志
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(request)
                ))
                
                FileLogger.logHttpRequest("POST", url, body = requestBody)
                Log.d(TAG, "Requesting transaction attestation for device: $deviceId, amount: $amount")
                
                val api = NetworkModule.getApi(baseUrl)
                val response = api.attestTransaction(request)
                val duration = System.currentTimeMillis() - startTime
                
                // 记录响应日志
                val responseBody = response.body()
                val responseBodyJson = gson.toJson(responseBody)
                val statusCode = response.code()
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = responseBodyJson,
                    statusCode = statusCode,
                    duration = duration
                ))
                
                FileLogger.logHttpResponse(statusCode, url, body = responseBodyJson, duration = duration)
                FileLogger.i(TAG, "响应状态码: $statusCode")
                
                if (response.isSuccessful && responseBody != null) {
                    // 保存交易令牌
                    saveTransactionToken(
                        responseBody.transaction_token,
                        responseBody.expires_at
                    )
                    
                    val resultMessage = buildString {
                        appendLine("✅ 交易鉴证成功")
                        appendLine()
                        appendLine("交易令牌: ${responseBody.transaction_token.take(50)}...")
                        appendLine("过期时间: ${responseBody.expires_at}")
                        appendLine("设备状态: ${responseBody.device_status}")
                        appendLine("安全评分: ${responseBody.security_score}")
                        appendLine()
                        appendLine("💡 令牌已保存，可以进行交易处理")
                    }
                    
                    Log.i(TAG, "Transaction attestation successful, token expires at: ${responseBody.expires_at}")
                    FileLogger.i(TAG, "交易鉴证成功: token=${responseBody.transaction_token.take(20)}..., expires_at=${responseBody.expires_at}")
                    FileLogger.i(TAG, "========== 交易鉴证完成 ==========")
                    Result.success(resultMessage)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Attestation failed: ${response.code()} $errorBody")
                    
                    FileLogger.logErrorResponse(statusCode, url, errorBody)
                    FileLogger.e(TAG, "交易鉴证失败: code=$statusCode, error=$errorBody")
                    FileLogger.i(TAG, "========== 交易鉴证失败 ==========")
                    
                    Result.failure(Exception("鉴证失败: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transaction attestation", e)
                FileLogger.e(TAG, "交易鉴证异常", e)
                FileLogger.i(TAG, "========== 交易鉴证异常 ==========")
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "ERROR",
                    method = "POST",
                    url = "${baseUrl}api/v1/transactions/attest",
                    body = e.message ?: "Unknown error"
                ))
                Result.failure(e)
            }
        }
    }
    
    /**
     * 步骤2：处理交易 - 使用交易令牌
     * 
     * 这个步骤会：
     * 1. 使用之前获取的交易令牌
     * 2. 发送交易数据（卡号、PIN等）
     * 3. 完成交易处理
     */
    suspend fun processTransaction(
        baseUrl: String,
        deviceId: String,
        cardNumber: String,
        amount: Long,
        currency: String = "CNY",
        onLog: (ApiLog) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查是否有保存的令牌
                val transactionToken = getSavedTransactionToken()
                if (transactionToken == null) {
                    return@withContext Result.failure(
                        Exception("❌ 没有可用的交易令牌，请先进行交易鉴证")
                    )
                }
                
                val startTime = System.currentTimeMillis()
                
                // 获取设备的真实 KSN
                val deviceManager = DeviceManager(context)
                val ksn = deviceManager.getKsn() ?: run {
                    return@withContext Result.failure(
                        Exception("❌ 设备未注册或未注入密钥，无法获取 KSN")
                    )
                }
                
                // 模拟加密的PIN块（实际应用中应该使用真实的加密）
                val encryptedPinBlock = "SIMULATED_ENCRYPTED_PIN_BLOCK_${System.currentTimeMillis()}"
                
                // 掩码卡号（只显示前6位和后4位）
                val maskedCardNumber = if (cardNumber.length >= 10) {
                    "${cardNumber.substring(0, 6)}****${cardNumber.substring(cardNumber.length - 4)}"
                } else {
                    cardNumber
                }
                
                // 获取位置信息
                val locationHelper = com.sunbay.softpos.utils.LocationHelper(context)
                val (clientIp, locationData) = locationHelper.getFullLocationInfo()
                
                Log.d(TAG, "位置信息: IP=$clientIp, Location=$locationData")
                
                // 构建交易请求
                val request = ProcessTransactionRequest(
                    device_id = deviceId,
                    transaction_type = "PAYMENT",
                    amount = amount,
                    currency = currency,
                    encrypted_pin_block = encryptedPinBlock,
                    ksn = ksn,
                    card_number_masked = maskedCardNumber,
                    transaction_token = transactionToken,
                    client_ip = clientIp,
                    latitude = locationData?.latitude,
                    longitude = locationData?.longitude,
                    location_accuracy = locationData?.accuracy,
                    location_timestamp = locationData?.timestamp
                )
                
                val url = "${baseUrl}api/v1/transactions/process"
                
                // 记录请求日志
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(request)
                ))
                
                Log.d(TAG, "Processing transaction with token")
                
                val api = NetworkModule.getApi(baseUrl)
                val response = api.processTransaction(request)
                val duration = System.currentTimeMillis() - startTime
                
                // 记录响应日志
                val responseBody = response.body()
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = gson.toJson(responseBody),
                    statusCode = response.code(),
                    duration = duration
                ))
                
                if (response.isSuccessful && responseBody != null) {
                    // 交易成功后清除令牌（一次性使用）
                    clearTransactionToken()
                    
                    val resultMessage = buildString {
                        appendLine("✅ 交易处理成功")
                        appendLine()
                        appendLine("交易ID: ${responseBody.transaction_id}")
                        appendLine("状态: ${responseBody.status}")
                        appendLine("处理时间: ${responseBody.processed_at}")
                        appendLine()
                        appendLine("💡 令牌已使用并清除")
                    }
                    
                    Log.i(TAG, "Transaction processed successfully: ${responseBody.transaction_id}")
                    Result.success(resultMessage)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Transaction processing failed: ${response.code()} $errorBody")
                    
                    // 如果是令牌过期或无效，清除保存的令牌
                    if (response.code() == 401 || response.code() == 403) {
                        clearTransactionToken()
                    }
                    
                    Result.failure(Exception("交易处理失败: ${response.code()} - $errorBody"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transaction processing", e)
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "ERROR",
                    method = "POST",
                    url = "${baseUrl}api/v1/transactions/process",
                    body = e.message ?: "Unknown error"
                ))
                Result.failure(e)
            }
        }
    }
    
    /**
     * 完整的交易流程演示
     * 
     * 这个方法演示了完整的交易流程：
     * 1. 交易鉴证（获取令牌）
     * 2. 交易处理（使用令牌）
     */
    suspend fun demonstrateFullTransactionFlow(
        baseUrl: String,
        deviceId: String,
        cardNumber: String,
        amount: Long,
        currency: String = "CNY",
        onLog: (ApiLog) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<String>()
                
                // 步骤1：交易鉴证
                results.add("=== 步骤1：交易鉴证 ===")
                val attestResult = attestTransaction(baseUrl, deviceId, amount, currency, onLog)
                
                if (attestResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("交易鉴证失败: ${attestResult.exceptionOrNull()?.message}")
                    )
                }
                
                results.add(attestResult.getOrNull() ?: "")
                results.add("")
                
                // 等待一小段时间，模拟用户输入PIN的过程
                kotlinx.coroutines.delay(1000)
                
                // 步骤2：交易处理
                results.add("=== 步骤2：交易处理 ===")
                val processResult = processTransaction(baseUrl, deviceId, cardNumber, amount, currency, onLog)
                
                if (processResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("交易处理失败: ${processResult.exceptionOrNull()?.message}")
                    )
                }
                
                results.add(processResult.getOrNull() ?: "")
                
                Result.success(results.joinToString("\n"))
            } catch (e: Exception) {
                Log.e(TAG, "Error during full transaction flow", e)
                Result.failure(e)
            }
        }
    }
}
