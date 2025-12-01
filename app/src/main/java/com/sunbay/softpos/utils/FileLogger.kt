package com.sunbay.softpos.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志工具类
 * 用于将调试日志写入文件，方便追踪问题
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_DIR = "softpos_logs"
    private const val MAX_LOG_FILES = 10
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    private var currentLogFile: File? = null
    private var context: Context? = null
    
    /**
     * 初始化文件日志
     */
    fun init(context: Context) {
        this.context = context
        createNewLogFile()
        cleanOldLogs()
    }
    
    /**
     * 创建新的日志文件
     */
    private fun createNewLogFile() {
        try {
            val logDir = File(context?.getExternalFilesDir(null), LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val fileName = "softpos_${fileNameFormat.format(Date())}.log"
            currentLogFile = File(logDir, fileName)
            
            Log.i(TAG, "Log file created: ${currentLogFile?.absolutePath}")
            
            // 写入文件头
            writeToFile("=" * 80)
            writeToFile("SoftPOS Debug Log")
            writeToFile("Started at: ${dateFormat.format(Date())}")
            writeToFile("=" * 80)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log file", e)
        }
    }
    
    /**
     * 清理旧的日志文件
     */
    private fun cleanOldLogs() {
        try {
            val logDir = File(context?.getExternalFilesDir(null), LOG_DIR)
            if (!logDir.exists()) return
            
            val logFiles = logDir.listFiles()?.sortedByDescending { it.lastModified() }
            if (logFiles != null && logFiles.size > MAX_LOG_FILES) {
                logFiles.drop(MAX_LOG_FILES).forEach { file ->
                    file.delete()
                    Log.d(TAG, "Deleted old log file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old logs", e)
        }
    }
    
    /**
     * 检查并轮转日志文件
     */
    private fun checkAndRotateLog() {
        currentLogFile?.let { file ->
            if (file.length() > MAX_LOG_SIZE) {
                createNewLogFile()
            }
        }
    }
    
    /**
     * 写入日志到文件
     */
    private fun writeToFile(message: String) {
        try {
            currentLogFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.append(message)
                    writer.append("\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
    
    /**
     * 记录调试日志
     */
    fun d(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [DEBUG] [$tag] $message"
        Log.d(tag, message)
        writeToFile(logMessage)
        checkAndRotateLog()
    }
    
    /**
     * 记录信息日志
     */
    fun i(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [INFO] [$tag] $message"
        Log.i(tag, message)
        writeToFile(logMessage)
        checkAndRotateLog()
    }
    
    /**
     * 记录警告日志
     */
    fun w(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [WARN] [$tag] $message"
        Log.w(tag, message)
        writeToFile(logMessage)
        checkAndRotateLog()
    }
    
    /**
     * 记录错误日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = if (throwable != null) {
            "[$timestamp] [ERROR] [$tag] $message\n${throwable.stackTraceToString()}"
        } else {
            "[$timestamp] [ERROR] [$tag] $message"
        }
        Log.e(tag, message, throwable)
        writeToFile(logMessage)
        checkAndRotateLog()
    }
    
    /**
     * 记录 HTTP 请求
     */
    fun logHttpRequest(method: String, url: String, headers: Map<String, String>? = null, body: String? = null) {
        val timestamp = dateFormat.format(Date())
        writeToFile("\n${"=" * 80}")
        writeToFile("[$timestamp] HTTP REQUEST")
        writeToFile("Method: $method")
        writeToFile("URL: $url")
        
        if (headers != null && headers.isNotEmpty()) {
            writeToFile("Headers:")
            headers.forEach { (key, value) ->
                writeToFile("  $key: $value")
            }
        }
        
        if (body != null) {
            writeToFile("Body:")
            writeToFile(body)
        }
        writeToFile("=" * 80)
        checkAndRotateLog()
    }
    
    /**
     * 记录 HTTP 响应
     */
    fun logHttpResponse(statusCode: Int, url: String, headers: Map<String, String>? = null, body: String? = null, duration: Long? = null) {
        val timestamp = dateFormat.format(Date())
        writeToFile("\n${"=" * 80}")
        writeToFile("[$timestamp] HTTP RESPONSE")
        writeToFile("Status Code: $statusCode")
        writeToFile("URL: $url")
        
        if (duration != null) {
            writeToFile("Duration: ${duration}ms")
        }
        
        if (headers != null && headers.isNotEmpty()) {
            writeToFile("Headers:")
            headers.forEach { (key, value) ->
                writeToFile("  $key: $value")
            }
        }
        
        if (body != null) {
            writeToFile("Body:")
            writeToFile(body)
        }
        writeToFile("=" * 80)
        checkAndRotateLog()
    }
    
    /**
     * 记录错误响应的详细信息
     */
    fun logErrorResponse(statusCode: Int, url: String, errorBody: String?, exception: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        writeToFile("\n${"!" * 80}")
        writeToFile("[$timestamp] ERROR RESPONSE")
        writeToFile("Status Code: $statusCode")
        writeToFile("URL: $url")
        
        if (errorBody != null) {
            writeToFile("Error Body:")
            writeToFile(errorBody)
        }
        
        if (exception != null) {
            writeToFile("Exception:")
            writeToFile(exception.stackTraceToString())
        }
        writeToFile("!" * 80)
        checkAndRotateLog()
    }
    
    /**
     * 获取当前日志文件路径
     */
    fun getCurrentLogPath(): String? {
        return currentLogFile?.absolutePath
    }
    
    /**
     * 获取所有日志文件
     */
    fun getAllLogFiles(): List<File> {
        val logDir = File(context?.getExternalFilesDir(null), LOG_DIR)
        return logDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }
    
    /**
     * 分隔线
     */
    private operator fun String.times(count: Int): String {
        return this.repeat(count)
    }
}
