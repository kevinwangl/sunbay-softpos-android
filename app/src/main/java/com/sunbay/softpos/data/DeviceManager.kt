package com.sunbay.softpos.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sunbay.softpos.network.DeviceRegistrationRequest
import com.sunbay.softpos.network.NetworkModule
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
                
                // Get real device information
                val deviceInfoHelper = DeviceInfoHelper(context)
                val deviceInfo = deviceInfoHelper.getDeviceInfo()
                
                // Get or create RSA key pair
                val keyManager = com.sunbay.softpos.security.KeyManager(context)
                val publicKey = keyManager.getOrCreatePublicKey()

                val request = DeviceRegistrationRequest(
                    imei = deviceInfo.imei,  // Using 15-digit Virtual IMEI derived from Android ID
                    model = deviceInfo.model,
                    os_version = deviceInfo.osVersion,
                    tee_type = deviceInfo.teeType,
                    public_key = publicKey,
                    device_mode = "FULL_POS"
                )
                
                val url = "${baseUrl}api/v1/devices/register"
                
                // Log request
                onLog(ApiLog(
                    timestamp = dateFormat.format(Date()),
                    type = "REQUEST",
                    method = "POST",
                    url = url,
                    body = gson.toJson(request)
                ))
                
                Log.d("DeviceManager", "Registering device to $baseUrl: $request")
                val api = NetworkModule.getApi(baseUrl)
                val response = api.registerDevice(request)
                val duration = System.currentTimeMillis() - startTime
                
                // Log response
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
                
                if (response.isSuccessful && responseBody?.code == 201) {
                    // Extract and save device_id from response
                    val dataJson = gson.toJson(responseBody.data)
                    val deviceIdRegex = "\"device_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val deviceId = deviceIdRegex.find(dataJson)?.groupValues?.get(1)
                    
                    if (deviceId != null) {
                        saveDeviceInfo(deviceId, deviceInfo.imei)
                        Log.i("DeviceManager", "Device registered successfully with ID: $deviceId")
                    }
                    
                    Result.success("Success: ${responseBody.message}\nData: $dataJson")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DeviceManager", "Registration failed: ${response.code()} $errorBody")
                    Result.failure(Exception("Registration failed: ${response.code()} - ${responseBody?.message ?: errorBody ?: "Unknown"}"))
                }
            } catch (e: Exception) {
                Log.e("DeviceManager", "Error registering device", e)
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
}
