package com.sunbay.softpos.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.Collections

/**
 * 位置信息辅助类
 * 用于获取设备的 GPS 位置和 IP 地址
 */
class LocationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationHelper"
    }
    
    /**
     * 位置数据类
     */
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: String
    )
    
    /**
     * 检查是否有位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取最后已知的位置
     * 注意：这是一个简化的实现，实际应用中应该使用 FusedLocationProviderClient
     */
    fun getLastKnownLocation(): LocationData? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "没有位置权限")
            return null
        }
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 尝试从 GPS 获取
            val gpsLocation = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) {
                Log.e(TAG, "获取 GPS 位置失败", e)
                null
            }
            
            // 如果 GPS 不可用，尝试从网络获取
            val networkLocation = try {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                Log.e(TAG, "获取网络位置失败", e)
                null
            }
            
            // 选择最新的位置
            val location = when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
            
            return location?.let {
                LocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy,
                    timestamp = java.time.Instant.ofEpochMilli(it.time)
                        .toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取位置失败", e)
            return null
        }
    }
    
    /**
     * 获取设备的 IP 地址
     * 返回第一个非回环地址
     */
    fun getClientIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        // 优先返回 IPv4 地址
                        if (hostAddress != null && hostAddress.indexOf(':') < 0) {
                            Log.d(TAG, "获取到 IP 地址: $hostAddress")
                        return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 IP 地址失败", e)
        }
        return null
    }
    
    /**
     * 获取完整的位置信息（包括 IP）
     */
    fun getFullLocationInfo(): Pair<String?, LocationData?> {
        val ip = getClientIp()
        val location = getLastKnownLocation()
        
        Log.d(TAG, "位置信息: IP=$ip, Location=$location")
        
        return Pair(ip, location)
    }
}
