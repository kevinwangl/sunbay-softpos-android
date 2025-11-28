# 交易令牌演示功能使用指南

## 概述

本文档介绍如何使用 Sunbay SoftPOS Android 应用中的交易令牌演示功能。交易令牌是 MPoC (Mobile Point of Contact) 认证的核心安全机制，用于确保交易的安全性和设备的合规性。

## 功能说明

### 什么是交易令牌？

交易令牌是一个短期有效的 JWT (JSON Web Token)，包含以下信息：
- 设备健康检查结果
- 安全评分
- 设备状态
- 交易限额
- 有效期（通常为 5 分钟）

### 交易流程

```
┌─────────────────┐
│  1. 交易鉴证     │  设备发送健康状态 → 后端验证 → 返回交易令牌
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  2. 交易处理     │  使用令牌 + 交易数据 → 后端验证令牌 → 处理交易
└─────────────────┘
```

## 使用步骤

### 前提条件

1. **后端服务运行**
   ```bash
   cd sunbay-softpos-backend
   cargo run --release
   ```

2. **设备已注册**
   - 在应用中点击 "Register Device" 按钮
   - 确保注册成功并获得 device_id

### 方式一：分步演示

#### 步骤 1：交易鉴证

1. 在 "金额 (分)" 输入框中输入交易金额（例如：10000 表示 100.00 元）
2. 在 "卡号" 输入框中输入测试卡号（例如：6222021234567890）
3. 点击 **"1. 交易鉴证"** 按钮

**预期结果：**
```
✅ 交易鉴证成功

交易令牌: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
过期时间: 2024-01-01T10:05:00Z
设备状态: ACTIVE
安全评分: 95

💡 令牌已保存，可以进行交易处理
```

**Log Input 显示：**
```json
POST /api/v1/transactions/attest
{
  "device_id": "dev-xxx",
  "amount": 10000,
  "currency": "CNY",
  "health_check": {
    "root_status": false,
    "debug_status": false,
    "hook_status": false,
    "emulator_status": false,
    "tee_status": true,
    "system_integrity": true,
    "app_integrity": true
  }
}
```

**Log Output 显示：**
```json
200 OK (123ms)
{
  "transaction_token": "eyJhbGc...",
  "expires_at": "2024-01-01T10:05:00Z",
  "device_status": "ACTIVE",
  "security_score": 95
}
```

#### 步骤 2：交易处理

1. 确保已完成步骤 1（交易鉴证）
2. 点击 **"2. 交易处理"** 按钮

**预期结果：**
```
✅ 交易处理成功

交易ID: txn-123456
状态: SUCCESS
处理时间: 2024-01-01T10:01:00Z

💡 令牌已使用并清除
```

**Log Input 显示：**
```json
POST /api/v1/transactions/process
{
  "transaction_token": "eyJhbGc...",
  "encrypted_pin_block": "SIMULATED_ENCRYPTED_PIN_BLOCK_...",
  "ksn": "FFFF9876543210E00001",
  "card_number": "6222021234567890",
  "amount": 10000,
  "currency": "CNY"
}
```

**Log Output 显示：**
```json
200 OK (89ms)
{
  "transaction_id": "txn-123456",
  "status": "SUCCESS",
  "processed_at": "2024-01-01T10:01:00Z"
}
```

### 方式二：完整流程演示

点击 **"完整流程演示"** 按钮，自动执行以下步骤：
1. 交易鉴证（获取令牌）
2. 等待 1 秒（模拟用户输入 PIN）
3. 交易处理（使用令牌）

**预期结果：**
```
=== 步骤1：交易鉴证 ===
✅ 交易鉴证成功
...

=== 步骤2：交易处理 ===
✅ 交易处理成功
...
```

### 查看令牌状态

点击 **"查看令牌"** 按钮，查看当前保存的交易令牌：

**有令牌时：**
```
令牌: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9eyJzdWIiOi...
过期时间: 2024-01-01T10:05:00Z
```

**无令牌时：**
```
没有可用的交易令牌
```

## 错误处理

### 常见错误及解决方案

#### 1. "请先注册设备"

**原因：** 设备未注册或 device_id 未保存

**解决方案：**
1. 点击 "Register Device" 按钮
2. 等待注册成功
3. 重新尝试交易鉴证

#### 2. "❌ 没有可用的交易令牌，请先进行交易鉴证"

**原因：** 直接点击 "2. 交易处理" 但没有先进行交易鉴证

**解决方案：**
1. 先点击 "1. 交易鉴证" 获取令牌
2. 再点击 "2. 交易处理"

#### 3. "鉴证失败: 401 - Unauthorized"

**原因：** 设备状态不是 ACTIVE（可能是 PENDING_APPROVAL、SUSPENDED 或 REVOKED）

**解决方案：**
1. 检查后端日志，确认设备状态
2. 如果是 PENDING_APPROVAL，需要在后端或前端管理界面审批设备
3. 如果是 SUSPENDED，需要恢复设备
4. 如果是 REVOKED，需要重新注册设备

#### 4. "交易处理失败: 401 - Token expired"

**原因：** 交易令牌已过期（超过 5 分钟）

**解决方案：**
1. 重新点击 "1. 交易鉴证" 获取新令牌
2. 在 5 分钟内完成交易处理

#### 5. "交易处理失败: 403 - Token already used"

**原因：** 交易令牌已被使用（一次性令牌）

**解决方案：**
1. 重新点击 "1. 交易鉴证" 获取新令牌
2. 使用新令牌进行交易

## 技术细节

### 交易令牌的生命周期

```
┌──────────────┐
│ 1. 生成令牌  │  健康检查 → 生成 JWT → 保存到 Redis
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 2. 使用令牌  │  验证 JWT → 检查是否已使用 → 标记为已使用
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 3. 令牌失效  │  过期（5分钟）或已使用
└──────────────┘
```

### 令牌内容示例

```json
{
  "health_check_id": "check-456",
  "security_score": 85,
  "device_status": "ACTIVE",
  "max_amount": 500000,
  "exp": 1700000300
}
```

### 安全机制

1. **短期有效**：令牌有效期仅 5 分钟
2. **一次性使用**：令牌使用后立即失效
3. **签名验证**：使用 HMAC-SHA256 签名，防止篡改
4. **设备绑定**：令牌与特定设备 ID 绑定
5. **健康快照**：包含设备健康状态的时间点快照

## 代码结构

### 新增文件

```
sunbay-softpos-android/app/src/main/java/com/sunbay/softpos/
├── data/
│   └── TransactionTokenManager.kt    # 交易令牌管理器
├── network/
│   └── BackendApi.kt                 # 新增交易相关 API 接口
└── security/
    └── ThreatDetector.kt             # 新增 performHealthCheck() 方法
```

### 核心类

#### TransactionTokenManager

负责交易令牌的管理：
- `attestTransaction()` - 交易鉴证，获取令牌
- `processTransaction()` - 交易处理，使用令牌
- `demonstrateFullTransactionFlow()` - 完整流程演示
- `getSavedTransactionToken()` - 获取保存的令牌
- `clearTransactionToken()` - 清除令牌

#### 新增 API 接口

```kotlin
// 交易鉴证
@POST("/api/v1/transactions/attest")
suspend fun attestTransaction(@Body request: TransactionAttestRequest): Response<TransactionAttestResponse>

// 交易处理
@POST("/api/v1/transactions/process")
suspend fun processTransaction(@Body request: ProcessTransactionRequest): Response<ProcessTransactionResponse>
```

## 测试场景

### 场景 1：正常交易流程

1. 注册设备
2. 交易鉴证（金额：10000 分）
3. 交易处理
4. 验证交易成功

### 场景 2：令牌过期

1. 交易鉴证
2. 等待 6 分钟
3. 交易处理
4. 验证返回 "Token expired" 错误
5. 重新交易鉴证
6. 交易处理成功

### 场景 3：令牌重复使用

1. 交易鉴证
2. 交易处理（成功）
3. 再次交易处理（使用同一令牌）
4. 验证返回 "Token already used" 错误

### 场景 4：设备状态变化

1. 交易鉴证（设备状态：ACTIVE）
2. 在后端暂停设备
3. 交易处理
4. 验证返回 "Device not active" 错误

### 场景 5：低安全评分

1. 在模拟器或 Root 设备上运行
2. 交易鉴证
3. 验证安全评分较低
4. 根据评分，交易可能被拒绝或限额

## 后续改进

### 当前实现的限制

1. **模拟 PIN 加密**：当前使用模拟的 PIN 块，实际应用需要使用真实的 DUKPT 加密
2. **简化的健康检查**：某些检查项（如 Hook 检测）尚未实现
3. **无 TEE 集成**：未集成真实的 TEE (Trusted Execution Environment)

### 建议的改进

1. **集成真实的 PIN 加密**
   ```kotlin
   val keyManager = KeyManager(context)
   val encryptedPinBlock = keyManager.encryptPin(pin, cardNumber)
   ```

2. **增强健康检查**
   - Hook 检测
   - 内存完整性检查
   - 反调试检测

3. **TEE 集成**
   - 使用 Android Keystore 的 StrongBox
   - 集成设备厂商的 TEE SDK

4. **用户体验优化**
   - 添加 PIN 输入界面
   - 显示交易进度
   - 添加交易历史记录

## 参考资料

- [MPoC 认证要求](../MPOC_AOV_COMPLIANCE_ANALYSIS.md)
- [交易令牌流程分析](../TRANSACTION_TOKEN_FLOW_ANALYSIS.md)
- [后端 API 文档](../sunbay-softpos-backend/API_DOCUMENTATION.md)
- [交易令牌设计文档](../TRANSACTION_TOKEN_DESIGN.md)

## 联系支持

如有问题，请查看：
1. 应用日志（Log Input / Log Output）
2. 后端日志（`sunbay-softpos-backend/backend.log`）
3. 项目文档和 README

---

**版本：** 1.0.0  
**更新日期：** 2024-01-01  
**作者：** Sunbay SoftPOS Team
