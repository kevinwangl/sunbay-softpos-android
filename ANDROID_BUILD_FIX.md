# Android 项目构建问题解决方案

## 问题诊断

构建失败的原因：**缺少 Android SDK 配置**

错误信息：
```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment 
variable or by setting the sdk.dir path in your project's local properties file
```

## 解决方案

### 方案一：使用 Android Studio（强烈推荐）

这是最简单、最可靠的方法：

1. **安装 Android Studio**
   - 下载地址：https://developer.android.com/studio
   - Android Studio 会自动安装和配置 Android SDK

2. **打开项目**
   - 启动 Android Studio
   - 选择 "Open" 打开 `sunbay-softpos-android` 目录
   - Android Studio 会自动检测并配置 SDK

3. **构建 APK**
   - 等待 Gradle 同步完成
   - 点击 `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
   - APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 方案二：手动配置 SDK（命令行构建）

如果您已经安装了 Android SDK，需要配置 SDK 路径：

#### 步骤 1：找到 Android SDK 路径

常见路径：
- **macOS**: `~/Library/Android/sdk`
- **Windows**: `C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk`
- **Linux**: `~/Android/Sdk`

#### 步骤 2：创建 local.properties 文件

在项目根目录创建 `local.properties` 文件：

```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

**注意**：将 `YOUR_USERNAME` 替换为您的实际用户名。

#### 步骤 3：设置 Java 环境

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
```

#### 步骤 4：构建 APK

```bash
./gradlew assembleDebug
```

### 方案三：安装 Android SDK（如果未安装）

#### 使用 Android Studio 安装（推荐）
1. 下载并安装 Android Studio
2. 首次启动时会自动下载 SDK

#### 使用命令行工具安装
1. 下载 Android Command Line Tools：
   https://developer.android.com/studio#command-tools

2. 解压并设置环境变量：
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
   ```

3. 安装必要的 SDK 组件：
   ```bash
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```

## 当前项目状态

✅ 已完成：
- Gradle Wrapper 配置（gradle-wrapper.jar, gradle-wrapper.properties, gradlew）
- 项目结构和代码文件
- 构建配置文件（build.gradle.kts, settings.gradle.kts）

❌ 需要配置：
- Android SDK 路径（local.properties）
- 或者使用 Android Studio 自动配置

## 快速开始（推荐流程）

1. 安装 Android Studio
2. 用 Android Studio 打开项目
3. 等待自动配置完成
4. 点击 Build -> Build APK
5. 完成！

## 文件说明

- `local.properties.template` - SDK 配置模板文件
- `BUILD_GUIDE.md` - 详细构建指南
- `gradlew` - Gradle Wrapper 执行脚本
- `gradle/wrapper/` - Gradle Wrapper 配置文件

## 需要帮助？

如果遇到问题，请检查：
1. Android Studio 是否正确安装
2. SDK 路径是否正确
3. Java 版本是否为 17 或更高
4. 网络连接是否正常（首次构建需要下载依赖）
