# Android 400 错误修复指南

## 问题描述
Android 端在处理交易时收到 400 Bad Request 错误。

## 根本原因
后端 API 已更新为使用 camelCase 字段名，但 Android 端仍在使用 snake_case。

## 修复内容

### 1. 添加 Gson SerializedName 注解

在 `BackendApi.kt` 文件顶部添加导入：

```kotlin
import com.google.gson.annotations.SerializedName
```

### 2. 修复 TransactionAttestRequest

```kotlin
// 交易鉴证请求
data class TransactionAttestRequest(
    @SerializedName("deviceId")
    val device_id: String,
    val amount: Long,
    val currency: String
)
```

### 3. 修复 ProcessTransactionRequest

```kotlin
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
```

## 字段名映射表

| Android 内部字段 (snake_case) | JSON 字段 (camelCase) |
|------------------------------|----------------------|
| device_id                    | deviceId             |
| transaction_type             | transactionType      |
| encrypted_pin_block          | encryptedPinBlock    |
| card_number_masked           | cardNumberMasked     |
| transaction_token            | transactionToken     |

## 枚举值格式

确保所有枚举值使用全大写格式：

### 交易类型
- ✅ "PAYMENT"
- ✅ "REFUND"
- ✅ "VOID"
- ✅ "PREAUTH"
- ✅ "CAPTURE"
- ❌ "Payment" (错误)
- ❌ "payment" (错误)

### TEE 类型
- ✅ "TRUST_ZONE"
- ✅ "SECURE_ELEMENT"
- ✅ "SOFTWARE"
- ❌ "TrustZone" (错误)

### 设备模式
- ✅ "FULL_POS"
- ✅ "PINPAD_ONLY"
- ❌ "FullPos" (错误)

## 交易令牌有效期

交易令牌只有 **5 分钟**有效期：

1. 调用 `/api/v1/transactions/attest` 获取令牌
2. **立即**调用 `/api/v1/transactions/process` 处理交易
3. 不要重复使用已用过的令牌

## 测试步骤

### 1. 重新编译 Android 应用

```bash
cd sunbay-softpos-android
./gradlew clean assembleDebug
```

### 2. 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 测试交易流程

1. 打开应用
2. 注册设备（如果是新设备）
3. 等待后台审批设备
4. 执行交易
5. 检查后端日志，应该看到 200 响应

## 验证修复

运行测试脚本验证：

```bash
cd sunbay-softpos-android
./setup-new-device-and-test.sh
```

应该看到所有步骤都返回 200 状态码。

## 常见错误

### 错误 1: 400 Bad Request - "Invalid field name"
**原因**: 字段名不匹配  
**解决**: 确保使用 @SerializedName 注解

### 错误 2: 400 Bad Request - "Invalid transaction type"
**原因**: 枚举值格式错误  
**解决**: 使用全大写格式 "PAYMENT"

### 错误 3: 401 Unauthorized - "Invalid or expired token"
**原因**: 交易令牌过期  
**解决**: 鉴证后立即处理交易，不要等待超过 5 分钟

### 错误 4: 400 Bad Request - "Token already used"
**原因**: 重复使用令牌  
**解决**: 每次交易都需要新的令牌

## 后端日志检查

如果仍然有问题，检查后端日志：

```bash
# 查看最近的错误
tail -f sunbay-softpos-backend/logs/app.log | grep ERROR

# 或者查看进程输出
# 找到后端进程 ID，然后查看输出
```

## 相关文件

- `app/src/main/java/com/sunbay/softpos/network/BackendApi.kt` - API 定义
- `app/src/main/java/com/sunbay/softpos/data/TransactionTokenManager.kt` - 交易处理逻辑
- `app/src/main/java/com/sunbay/softpos/data/DeviceManager.kt` - 设备管理

## 参考文档

- [TRANSACTION_DISPLAY_FIX_SUMMARY.md](../../TRANSACTION_DISPLAY_FIX_SUMMARY.md) - 后端修复总结
- [TRANSACTION_TOKEN_IMPLEMENTATION_SUMMARY.md](./TRANSACTION_TOKEN_IMPLEMENTATION_SUMMARY.md) - 交易令牌实现
