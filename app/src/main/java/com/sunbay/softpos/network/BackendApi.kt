package com.sunbay.softpos.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class DeviceRegistrationRequest(
    val imei: String,
    val model: String,
    val os_version: String,
    val tee_type: String,
    val public_key: String,
    val device_mode: String
)

data class DeviceRegistrationResponse(
    val code: Int,
    val message: String,
    val data: Any?
)

data class HealthCheckResponse(
    val status: String,
    val timestamp: String
)

data class ThreatReportRequest(
    val deviceId: String,
    val threatType: String,
    val severity: String,
    val description: String
)

data class ThreatReportResponse(
    val code: Int,
    val message: String,
    val data: ThreatData?
)

data class ThreatData(
    val id: String,
    val deviceId: String,
    val threatType: String,
    val severity: String,
    val status: String,
    val description: String,
    val detectedAt: String
)

interface BackendApi {
    @POST("/api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>
    
    @GET("/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>
    
    @POST("/api/v1/threats/report")
    suspend fun reportThreat(@Body request: ThreatReportRequest): Response<ThreatReportResponse>
}
