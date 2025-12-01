# Android 422/400 错误 Root Cause 分析

## 问题描述
Android 端交易处理返回 400 Bad Request 错误，但交易令牌验证成功。

## Root Cause

**KSN 不匹配**

Android 端使用硬编码的 KSN：
```kotlin
val ksn = "FFFF9876543210E00001"  // 硬编码的假 KSN
```

但后端验证时使用设备的真实 KSN（从密钥注入时生成）：
```rust
if &request.ksn != device_ksn {
    return Err(AppError::BadRequest("Invalid KSN".to_string()));
}
```

## 错误流程

1. ✅ Android 发送交易处理请求
2. ✅ 后端验证交易令牌成功
3. ✅ 后端开始处理交易
4. ❌ 后端验证 KSN 失败（硬编码的 KSN ≠ 设备真实 KSN）
5. ❌ 返回 400 Bad Request

## 日志证据

```
2025-12-01T09:22:13.749734Z  INFO  Transaction token verified for device 9e180285-b015-4954-83e0-ab7338104c3e
2025-12-01T09:22:13.749763Z  INFO  Processing transaction for device: 9e180285-b015-4954-83e0-ab7338104c3e
2025-12-01T09:22:13.750129Z  WARN  └─ 📤 RESPONSE [400]
```

请求体中的 KSN：
```json
{
  "ksn": "FFFF9876543210E00001",  // 硬编码的假 KSN
  ...
}
```

## 修复方案

### 修复代码

在 `TransactionTokenManager.kt` 中，从 DeviceManager 获取真实的 KSN：

```kotlin
// 获取设备的真实 KSN
val deviceManager = DeviceManager(context)
val ksn = deviceManager.getKsn() ?: run {
    return@withContext Result.failure(
        Exception("❌ 设备未注册或未注入密钥，无法获取 KSN")
    )
}

// 模拟加密的PIN块（实际应用中应该使用真实的加密）
val encryptedPinBlock = "SIMULATED_ENCRYPTED_PIN_BLOCK_${System.currentTimeMillis()}"
```

### 为什么测试脚本成功？

测试脚本 `setup-new-device-and-test.sh` 成功是因为：

1. 它注册新设备
2. 获取密钥注入响应中的 KSN
3. 使用这个真实的 KSN 进行交易

```bash
# 从注入响应中提取 KSN
CURRENT_KSN=$(echo "$INJECT_BODY" | jq -r '.ksn // empty')

# 使用真实的 KSN 进行交易
PROCESS_REQUEST=$(cat <<EOF
{
  ...
  "ksn": "$CURRENT_KSN",  # 使用真实的 KSN
  ...
}
EOF
)
```

## 验证修复

### 1. 重新编译 Android 应用

```bash
cd sunbay-softpos-android
./gradlew clean assembleDebug
```

### 2. 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 测试流程

1. 打开应用
2. 如果是新设备，先注册设备
3. 等待后台审批设备
4. 注入密钥（这会保存真实的 KSN）
5. 执行交易（现在会使用真实的 KSN）

### 4. 预期结果

后端日志应该显示：
```
Transaction token verified for device xxx
Processing transaction for device: xxx
RESPONSE [200]  # 成功！
```

## 相关文件

- `app/src/main/java/com/sunbay/softpos/data/TransactionTokenManager.kt` - 修复 KSN 获取
- `app/src/main/java/com/sunbay/softpos/data/DeviceManager.kt` - 存储和获取 KSN
- `sunbay-softpos-backend/src/services/transaction.rs` - KSN 验证逻辑

## 其他已修复的问题

1. ✅ 字段名映射（camelCase）
2. ✅ 交易类型枚举值（PAYMENT）
3. ✅ 后端请求 DTO 支持 camelCase
4. ✅ KSN 验证（本次修复）

## 总结

**Root Cause**: Android 端使用硬编码的假 KSN，而后端要求使用设备注册时生成的真实 KSN。

**Solution**: 从 DeviceManager 获取设备的真实 KSN，而不是使用硬编码值。

修复后，Android 端的交易流程应该能够正常工作！
