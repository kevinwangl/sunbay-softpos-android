# Android APK 构建指南

## 方法一：使用 Android Studio（推荐）

1. 打开 Android Studio
2. 选择 "Open" 并导航到 `sunbay-softpos-android` 目录
3. 等待 Gradle 同步完成
4. 点击菜单 `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
5. 构建完成后，APK 文件位于：`app/build/outputs/apk/debug/app-debug.apk`

## 方法二：使用命令行

### 前提条件
- 安装 Android SDK
- 设置 `ANDROID_HOME` 环境变量
- Java 17 或更高版本

### 构建步骤

```bash
# 设置 Java 环境（macOS with Homebrew）
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"

# 设置 Android SDK 路径（根据实际安装路径调整）
export ANDROID_HOME="$HOME/Library/Android/sdk"

# 进入项目目录
cd sunbay-softpos-android

# 使用 Gradle Wrapper 构建（如果存在）
./gradlew assembleDebug

# 或使用系统 Gradle
gradle assembleDebug
```

### 输出位置
构建成功后，APK 文件位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

## 安装到设备

```bash
# 通过 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或者直接将 APK 文件传输到 Android 设备并手动安装
```

## 常见问题

### 1. Gradle 版本不兼容
如果遇到 Gradle 版本问题，请确保使用 Gradle 8.x 版本。

### 2. Java 版本问题
Android Gradle Plugin 8.1.0 需要 Java 17 或更高版本。

### 3. Android SDK 未找到
确保已安装 Android SDK 并正确设置 `ANDROID_HOME` 环境变量。
