package com.sunbay.softpos.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sunbay.softpos.network.DeviceRegistrationRequest
import com.sunbay.softpos.network.NetworkModule
import com.sunbay.softpos.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class DeviceManager(private val context: Context) {
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IMEI = "imei"
        private const val KEY_KSN = "ksn"
    }
    
    /**
     * Get the saved device ID
     */
    fun getSavedDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }
    
    /**
     * Get the saved IMEI
     */
    fun getSavedImei(): String? {
        return prefs.getString(KEY_IMEI, null)
    }
    
    /**
     * Get the saved KSN (Key Serial Number)
     */
    fun getKsn(): String? {
        return prefs.getString(KEY_KSN, null)
    }
    
    /**
     * Save KSN after key injection
     */
    private fun saveKsn(ksn: String) {
        prefs.edit().apply {
            putString(KEY_KSN, ksn)
            apply()
        }
        Log.d("DeviceManager", "Saved KSN: $ksn")
    }
    
    /**
     * Save device ID and IMEI
     */
    private fun saveDeviceInfo(deviceId: String, imei: String) {
        prefs.edit().apply {
            putString(KEY_DEVICE_ID, deviceId)
            putString(KEY_IMEI, imei)
            apply()
        }
        Log.d("DeviceManager", "Saved device info: deviceId=$deviceId, imei=$imei")
    }

    suspend fun healthCheck(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl}health"
                
                // Log request
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "GET",
                    url = url
                ))
                
                val api = NetworkModule.getApi(baseUrl)
                val response = api.healthCheck()
                val duration = System.currentTimeMillis() - startTime
                
                // Log response
                val responseBody = response.body()
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "GET",
                    url = url,
                    body = gson.toJson(responseBody),
                    statusCode = response.code(),
                    duration = duration
                ))
                
                if (response.isSuccessful && responseBody != null) {
                    Result.success("Health Check OK\nStatus: ${responseBody.status}\nTimestamp: ${responseBody.timestamp}")
                } else {
                    Result.failure(Exception("Health check failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("DeviceManager", "Health check error", e)
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "ERROR",
                    method = "GET",
                    url = "${baseUrl}health",
                    body = e.message ?: "Unknown error"
                ))
                Result.failure(e)
            }
        }
    }

    suspend fun registerDevice(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                FileLogger.i("DeviceManager", "========== 开始设备注册 ==========")
                
                // Get real device information
                val deviceInfoHelper = DeviceInfoHelper(context)
                val deviceInfo = deviceInfoHelper.getDeviceInfo()
                
                FileLogger.d("DeviceManager", "设备信息: IMEI=${deviceInfo.imei}, Model=${deviceInfo.model}")
                
                // Get or create RSA key pair
                val keyManager = com.sunbay.softpos.security.KeyManager(context)
                val publicKey = keyManager.getOrCreatePublicKey()

                val request = DeviceRegistrationRequest(
                    imei = deviceInfo.imei,  // Using 15-digit Virtual IMEI derived from Android ID
                    model = deviceInfo.model,
                    os_version = deviceInfo.osVersion,
                    tee_type = deviceInfo.teeType,
                    public_key = publicKey,
                    device_mode = "FULL_POS",
                    nfc_present = deviceInfo.nfcPresent
                )
                
                val url = "${baseUrl}api/v1/devices/register"
                val requestBody = gson.toJson(request)
                
                // Log request
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = requestBody
                ))
                
                FileLogger.logHttpRequest("POST", url, body = requestBody)
                Log.d("DeviceManager", "Registering device to $baseUrl: $request")
                
                val api = NetworkModule.getApi(baseUrl)
                val response = api.registerDevice(request)
                val duration = System.currentTimeMillis() - startTime
                
                // Log response
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
                FileLogger.i("DeviceManager", "响应状态码: $statusCode")
                
                if (response.isSuccessful && responseBody?.code == 201) {
                    // Extract and save device_id and ksn from response
                    val dataJson = gson.toJson(responseBody.data)
                    val deviceIdRegex = "\"device_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val ksnRegex = "\"ksn\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    
                    val deviceId = deviceIdRegex.find(dataJson)?.groupValues?.get(1)
                    val ksn = ksnRegex.find(dataJson)?.groupValues?.get(1)
                    
                    if (deviceId != null) {
                        saveDeviceInfo(deviceId, deviceInfo.imei)
                        Log.i("DeviceManager", "Device registered successfully with ID: $deviceId")
                    }
                    
                    if (ksn != null) {
                        saveKsn(ksn)
                        Log.i("DeviceManager", "KSN saved: $ksn")
                        FileLogger.i("DeviceManager", "KSN 已保存: $ksn")
                    }
                    
                    FileLogger.i("DeviceManager", "设备注册成功: device_id=$deviceId, ksn=$ksn")
                    FileLogger.i("DeviceManager", "========== 设备注册完成 ==========")
                    Result.success("Success: ${responseBody.message}\nData: $dataJson")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DeviceManager", "Registration failed: ${response.code()} $errorBody")
                    
                    FileLogger.logErrorResponse(statusCode, url, errorBody)
                    FileLogger.e("DeviceManager", "设备注册失败: code=${response.code()}, message=${responseBody?.message}, error=$errorBody")
                    FileLogger.i("DeviceManager", "========== 设备注册失败 ==========")
                    
                    Result.failure(Exception("Registration failed: ${response.code()} - ${responseBody?.message ?: errorBody ?: "Unknown"}"))
                }
            } catch (e: Exception) {
                Log.e("DeviceManager", "Error registering device", e)
                FileLogger.e("DeviceManager", "设备注册异常", e)
                FileLogger.i("DeviceManager", "========== 设备注册异常 ==========")
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "ERROR",
                    method = "POST",
                    url = "${baseUrl}api/v1/devices/register",
                    body = e.message ?: "Unknown error"
                ))
                Result.failure(e)
            }
        }
    }

    suspend fun login(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
        // Placeholder for login implementation
        // In a real app, this would take credentials and call /auth/login
        // For this demo, we'll simulate a successful login or call a mock endpoint if available
        return withContext(Dispatchers.IO) {
             try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl}api/v1/auth/login"
                
                // Mock login request
                val requestBody = mapOf("username" to "demo_user", "password" to "demo_pass")

                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(requestBody)
                ))

                // TODO: Implement actual network call when API is ready
                // For now, we simulate a network delay and success
                kotlinx.coroutines.delay(500)
                
                val duration = System.currentTimeMillis() - startTime
                
                val responseBody = mapOf("token" to "mock_token_12345", "user" to "demo_user")
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = gson.toJson(responseBody),
                    statusCode = 200,
                    duration = duration
                ))

                Result.success("Login Successful\nToken: mock_token_12345")
            } catch (e: Exception) {
                 Result.failure(e)
            }
        }
    }

    suspend fun logout(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
         return withContext(Dispatchers.IO) {
             try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl}api/v1/auth/logout"
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url
                ))

                // TODO: Implement actual network call
                kotlinx.coroutines.delay(300)
                
                val duration = System.currentTimeMillis() - startTime
                
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = "{\"message\": \"Logged out successfully\"}",
                    statusCode = 200,
                    duration = duration
                ))

                Result.success("Logout Successful")
            } catch (e: Exception) {
                 Result.failure(e)
            }
        }
    }

    suspend fun injectKey(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
             try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl}api/v1/keys/inject"
                
                val deviceId = getSavedDeviceId() ?: return@withContext Result.failure(Exception("Device ID not found"))
                
                // 检查是否已有 KSN（从注册时获得）
                val existingKsn = getKsn()
                if (existingKsn == null) {
                    return@withContext Result.failure(Exception("KSN not found. Please register device first."))
                }
                
                val requestBody = mapOf("device_id" to deviceId)

                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(requestBody)
                ))

                // TODO: Implement actual network call
                // 在实际实现中，后端会使用注册时生成的 KSN 来派生 IPEK
                // 并返回加密的 IPEK 和 KSN
                kotlinx.coroutines.delay(800)
                
                val duration = System.currentTimeMillis() - startTime
                
                // 使用已保存的 KSN（从注册响应中获得）
                // 在实际实现中，后端会返回加密的 IPEK
                val responseBody = mapOf(
                    "status" to "success", 
                    "key_type" to "TMK", 
                    "kcv" to "A1B2C3", 
                    "ksn" to existingKsn,
                    "encrypted_ipek" to "MOCK_ENCRYPTED_IPEK_BASE64"
                )

                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = gson.toJson(responseBody),
                    statusCode = 200,
                    duration = duration
                ))

                Result.success("Key Injection Successful\nType: TMK\nKCV: A1B2C3\nKSN: $existingKsn")
            } catch (e: Exception) {
                 Result.failure(e)
            }
        }
    }

    suspend fun attestPinpad(baseUrl: String, onLog: (ApiLog) -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
             try {
                val startTime = System.currentTimeMillis()
                val url = "${baseUrl}api/v1/pinpad/attest"
                
                val deviceId = getSavedDeviceId() ?: return@withContext Result.failure(Exception("Device ID not found"))
                 // Get real device information
                val deviceInfoHelper = DeviceInfoHelper(context)
                val deviceInfo = deviceInfoHelper.getDeviceInfo()
                
                val requestBody = mapOf(
                    "device_id" to deviceId,
                    "tee_type" to deviceInfo.teeType,
                    "attestation_data" to "mock_attestation_blob"
                )

                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(requestBody)
                ))

                // TODO: Implement actual network call
                kotlinx.coroutines.delay(600)
                
                val duration = System.currentTimeMillis() - startTime
                
                val responseBody = mapOf("verified" to true, "attestation_status" to "VALID")

                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "RESPONSE",
                    method = "POST",
                    url = url,
                    body = gson.toJson(responseBody),
                    statusCode = 200,
                    duration = duration
                ))

                Result.success("PinPad Attestation Successful\nStatus: VALID")
            } catch (e: Exception) {
                 Result.failure(e)
            }
        }
    }
}
