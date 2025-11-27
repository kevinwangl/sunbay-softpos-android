# Android 网络安全策略问题解决方案

## 问题描述
错误信息：`CLEARTEXT communication to 10.23.10.54 not permitted by network security policy`

这是 Android 9.0 (API 28) 及以上版本的安全特性，默认禁止明文 HTTP 通信。

## 解决方案

### 已实施的修复

1. **创建网络安全配置文件**
   - 文件位置：`app/src/main/res/xml/network_security_config.xml`
   - 配置：允许明文 HTTP 流量

2. **更新 AndroidManifest.xml**
   - 添加了 `android:networkSecurityConfig="@xml/network_security_config"` 属性

3. **重新构建 APK**
   - 新的 APK 已包含网络安全配置
   - 位置：`app/build/outputs/apk/debug/app-debug.apk`

## 使用说明

### 安装新的 APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 配置后端地址
在应用中输入后端地址时，使用以下格式之一：

**真机测试（局域网）**：
```
http://10.23.10.54:8080/
```

**模拟器测试**：
```
http://10.0.2.2:8080/
```

## 安全说明

⚠️ **注意**：此配置允许应用使用 HTTP 明文通信，仅用于开发和测试环境。

生产环境建议：
- 使用 HTTPS 加密通信
- 配置更严格的网络安全策略
- 仅允许特定域名的明文通信

## 文件变更

### network_security_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### AndroidManifest.xml
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

## 验证

APK 重新构建成功，现在应该可以正常访问后端服务了。
