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

// 交易鉴证请求
data class TransactionAttestRequest(
    val device_id: String,
    val amount: Long,
    val currency: String,
    val health_check: HealthCheckData
)

// 健康检查数据
data class HealthCheckData(
    val root_status: Boolean,
    val debug_status: Boolean,
    val hook_status: Boolean,
    val emulator_status: Boolean,
    val tee_status: Boolean,
    val system_integrity: Boolean,
    val app_integrity: Boolean
)

// 交易鉴证响应
data class TransactionAttestResponse(
    val transaction_token: String,
    val expires_at: String,
    val device_status: String,
    val security_score: Int
)

// 交易处理请求
data class ProcessTransactionRequest(
    val transaction_token: String,
    val encrypted_pin_block: String,
    val ksn: String,
    val card_number: String,
    val amount: Long,
    val currency: String
)

// 交易处理响应
data class ProcessTransactionResponse(
    val transaction_id: String,
    val status: String,
    val processed_at: String
)

interface BackendApi {
    @POST("/api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>
    
    @GET("/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>
    
    @POST("/api/v1/threats/report")
    suspend fun reportThreat(@Body request: ThreatReportRequest): Response<ThreatReportResponse>
    
    @POST("/api/v1/transactions/attest")
    suspend fun attestTransaction(@Body request: TransactionAttestRequest): Response<TransactionAttestResponse>
    
    @POST("/api/v1/transactions/process")
    suspend fun processTransaction(@Body request: ProcessTransactionRequest): Response<ProcessTransactionResponse>
}
