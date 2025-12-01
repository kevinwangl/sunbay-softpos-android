package com.sunbay.softpos.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File

/**
 * Threat detection service for identifying security threats
 */
class ThreatDetector(private val context: Context) {

    /**
     * Threat types matching backend enum
     */
    enum class ThreatType {
        ROOT_DETECTION,
        BOOTLOADER_UNLOCK,
        SYSTEM_TAMPER,
        APP_TAMPER,
        TEE_COMPROMISE,
        LOW_SECURITY_SCORE,
        CONSECUTIVE_LOW_SCORES,
        OTHER
    }

    /**
     * Threat severity levels matching backend enum
     */
    enum class ThreatSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Threat detection result
     */
    data class ThreatResult(
        val threatType: ThreatType,
        val severity: ThreatSeverity,
        val description: String,
        val detected: Boolean
    )

    /**
     * Perform comprehensive threat scan
     */
    fun performThreatScan(): List<ThreatResult> {
        val threats = mutableListOf<ThreatResult>()

        // Check for root access
        threats.add(detectRoot())

        // Check for debugger
        threats.add(detectDebugger())

        // Check for emulator
        threats.add(detectEmulator())

        // Check for app tampering
        threats.add(detectAppTampering())

        // Check for bootloader unlock
        threats.add(detectBootloaderUnlock())

        return threats
    }

    /**
     * Detect root access
     */
    private fun detectRoot(): ThreatResult {
        val rootIndicators = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        val isRooted = rootIndicators.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        } || checkSuCommand()

        return ThreatResult(
            threatType = ThreatType.ROOT_DETECTION,
            severity = if (isRooted) ThreatSeverity.CRITICAL else ThreatSeverity.LOW,
            description = if (isRooted) "Root access detected on device" else "No root access detected",
            detected = isRooted
        )
    }

    /**
     * Check if su command is available
     */
    private fun checkSuCommand(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect debugger attachment
     */
    private fun detectDebugger(): ThreatResult {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerConnected = android.os.Debug.isDebuggerConnected()

        val detected = isDebuggable || isDebuggerConnected

        return ThreatResult(
            threatType = ThreatType.APP_TAMPER,
            severity = if (detected) ThreatSeverity.HIGH else ThreatSeverity.LOW,
            description = when {
                isDebuggerConnected -> "Debugger is currently attached"
                isDebuggable -> "App is built in debug mode"
                else -> "No debugger detected"
            },
            detected = detected
        )
    }

    /**
     * Detect emulator environment
     */
    private fun detectEmulator(): ThreatResult {
        val emulatorIndicators = listOf(
            Build.FINGERPRINT.startsWith("generic"),
            Build.FINGERPRINT.startsWith("unknown"),
            Build.MODEL.contains("google_sdk"),
            Build.MODEL.contains("Emulator"),
            Build.MODEL.contains("Android SDK built for x86"),
            Build.MANUFACTURER.contains("Genymotion"),
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"),
            "goldfish" == Build.HARDWARE
        )

        val isEmulator = emulatorIndicators.any { it }

        return ThreatResult(
            threatType = ThreatType.SYSTEM_TAMPER,
            severity = if (isEmulator) ThreatSeverity.MEDIUM else ThreatSeverity.LOW,
            description = if (isEmulator) "Running on emulator" else "Running on physical device",
            detected = isEmulator
        )
    }

    /**
     * Detect app tampering (simplified check)
     */
    private fun detectAppTampering(): ThreatResult {
        // Check if app is installed from unknown sources
        val isFromUnknownSource = try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == null || installer.isEmpty()
        } catch (e: Exception) {
            true
        }

        return ThreatResult(
            threatType = ThreatType.APP_TAMPER,
            severity = if (isFromUnknownSource) ThreatSeverity.MEDIUM else ThreatSeverity.LOW,
            description = if (isFromUnknownSource) 
                "App installed from unknown source" 
            else 
                "App installed from trusted source",
            detected = isFromUnknownSource
        )
    }

    /**
     * Detect bootloader unlock status
     */
    private fun detectBootloaderUnlock(): ThreatResult {
        // This is a simplified check - actual implementation would vary by device
        val isUnlocked = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Check if device is unlocked (simplified)
                false // Most devices don't expose this easily
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

        return ThreatResult(
            threatType = ThreatType.BOOTLOADER_UNLOCK,
            severity = if (isUnlocked) ThreatSeverity.HIGH else ThreatSeverity.LOW,
            description = if (isUnlocked) "Bootloader is unlocked" else "Bootloader status unknown",
            detected = isUnlocked
        )
    }

    /**
     * Get only detected threats
     */
    fun getDetectedThreats(): List<ThreatResult> {
        return performThreatScan().filter { it.detected }
    }

    /**
     * Get highest severity threat
     */
    fun getHighestSeverityThreat(): ThreatResult? {
        val threats = getDetectedThreats()
        return threats.maxByOrNull { it.severity.ordinal }
    }
    
}
