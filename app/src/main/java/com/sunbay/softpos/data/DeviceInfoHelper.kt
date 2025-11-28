package com.sunbay.softpos.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyProperties

/**
 * Helper class to retrieve real device information
 */
class DeviceInfoHelper(private val context: Context) {
    
    /**
     * Get comprehensive device information
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            androidId = getAndroidId(),
            imei = getVirtualImei(), // Generate 15-digit Virtual IMEI
            model = getDeviceModel(),
            osVersion = getOsVersion(),
            teeType = detectTeeType(),
            manufacturer = getManufacturer(),
            sdkVersion = Build.VERSION.SDK_INT,
            nfcPresent = hasNfcSupport()
        )
    }
    
    /**
     * Generate a 15-digit Virtual IMEI based on Android ID
     * Backend requires exactly 15 digits, but Android ID is 16-char hex.
     * We convert Android ID to decimal and take first 15 digits to satisfy validation
     * while keeping it unique to the device.
     */
    private fun getVirtualImei(): String {
        val androidId = getAndroidId()
        if (androidId == "unknown") {
            // Fallback to random if Android ID is unavailable
            return (1..15).map { kotlin.random.Random.nextInt(0, 10) }.joinToString("")
        }

        return try {
            // Convert Hex Android ID to BigInteger, then to String
            // Android ID is 64-bit hex, so max value is ~1.84e19 (20 digits)
            val decimal = java.math.BigInteger(androidId, 16).toString()
            
            // Pad with leading zeros if needed (unlikely) and take first 15 digits
            decimal.padStart(15, '0').take(15)
        } catch (e: Exception) {
            // Fallback on error
            (1..15).map { kotlin.random.Random.nextInt(0, 10) }.joinToString("")
        }
    }
    
    /**
     * Get Android ID as device identifier
     * This is a unique identifier for each app on each device
     */
    private fun getAndroidId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
    
    /**
     * Get device model
     */
    private fun getDeviceModel(): String {
        return Build.MODEL ?: "unknown"
    }
    
    /**
     * Get Android OS version
     */
    private fun getOsVersion(): String {
        return Build.VERSION.RELEASE ?: "unknown"
    }
    
    /**
     * Get device manufacturer
     */
    private fun getManufacturer(): String {
        return Build.MANUFACTURER ?: "unknown"
    }
    
    /**
     * Detect the type of Trusted Execution Environment (TEE) supported by the device
     * Returns: QTEE (for StrongBox) or TRUSTZONE (for TEE or default)
     * Note: Backend only accepts QTEE or TRUSTZONE, so we map accordingly
     */
    private fun detectTeeType(): String {
        return when {
            // Check if device supports StrongBox (hardware-backed security)
            // Map StrongBox to QTEE (Qualcomm Trusted Execution Environment)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox() -> "QTEE"
            
            // For all other cases (TEE or no TEE), return TRUST_ZONE
            // This ensures compatibility with backend which only accepts QTEE or TRUST_ZONE
            else -> "TRUST_ZONE"
        }
    }
    
    /**
     * Check if device supports StrongBox Keymaster
     * StrongBox is a hardware security module (HSM) that provides the highest level of security
     */
    private fun hasStrongBox(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                context.packageManager.hasSystemFeature(
                    "android.hardware.strongbox_keystore"
                )
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    
    /**
     * Check if device has Trusted Execution Environment (TEE)
     * Most modern Android devices have TEE support (TrustZone on ARM)
     */
    private fun hasTrustedExecutionEnvironment(): Boolean {
        return try {
            // Check if hardware-backed keystore is available
            // This indicates TEE support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.packageManager.hasSystemFeature(
                    "android.hardware.keystore"
                )
            } else {
                // For older devices, assume TEE is available if device is from major manufacturer
                Build.MANUFACTURER.lowercase() in listOf("samsung", "google", "huawei", "xiaomi", "oppo", "vivo")
            }
        } catch (e: Exception) {
            false
        }
    }
    /**
     * Check if device supports NFC
     */
    private fun hasNfcSupport(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC)
    }
}

/**
 * Data class to hold device information
 */
data class DeviceInfo(
    val androidId: String,
    val imei: String, // Virtual 15-digit IMEI
    val model: String,
    val osVersion: String,
    val teeType: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val nfcPresent: Boolean
)
