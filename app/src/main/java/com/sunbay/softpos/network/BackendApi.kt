package com.sunbay.softpos.network

import com.google.gson.annotations.SerializedName
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
    val device_mode: String,
    val nfc_present: Boolean
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
    @SerializedName("deviceId")
    val device_id: String,
    val amount: Long,
    val currency: String
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
    @SerializedName("deviceId")
    val device_id: String,
    @SerializedName("transactionType")
    val transaction_type: String,
    val amount: Long,
    val currency: String,
    @SerializedName("encryptedPinBlock")
    val encrypted_pin_block: String,
    val ksn: String,
    @SerializedName("cardNumberMasked")
    val card_number_masked: String?,
    @SerializedName("transactionToken")
    val transaction_token: String
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
