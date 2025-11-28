# 交易令牌演示功能 - 快速开始

## 快速开始

### 1. 启动后端服务

```bash
cd sunbay-softpos-backend
cargo run --release
```

后端将在 `http://localhost:8080` 启动。

### 2. 构建 Android 应用

```bash
cd sunbay-softpos-android
./gradlew assembleDebug
```

或在 Android Studio 中打开项目并点击 "Run"。

### 3. 配置后端 URL

在应用中，将 "Backend URL" 修改为：
- 模拟器：`http://10.0.2.2:8080/`
- 真机（同一网络）：`http://YOUR_COMPUTER_IP:8080/`
- 示例：`http://10.23.10.54:8080/`

### 4. 使用演示功能

#### 基础流程

1. **Health Check** - 验证后端连接
2. **Register Device** - 注册设备
3. **输入交易信息**
   - 金额：10000（表示 100.00 元）
   - 卡号：6222021234567890
4. **完整流程演示** - 一键完成交易鉴证和处理

#### 分步流程

1. **1. 交易鉴证** - 获取交易令牌
2. **2. 交易处理** - 使用令牌完成交易
3. **查看令牌** - 查看当前令牌状态

## 功能说明

### 交易令牌演示区域

```
┌─────────────────────────────────────────┐
│ 交易令牌演示                             │
├─────────────────────────────────────────┤
│ 金额 (分): [10000]  卡号: [6222...]     │
├─────────────────────────────────────────┤
│ [1. 交易鉴证]  [2. 交易处理]            │
│ [完整流程演示]  [查看令牌]              │
└─────────────────────────────────────────┘
```

### 按钮功能

- **1. 交易鉴证**：发送设备健康状态，获取交易令牌
- **2. 交易处理**：使用令牌处理交易（需先完成鉴证）
- **完整流程演示**：自动执行鉴证 → 处理的完整流程
- **查看令牌**：显示当前保存的令牌和过期时间

### 日志查看

- **Log Input**：显示发送到后端的请求（橙色）
- **Log Output**：显示后端返回的响应（绿色）

## 预期结果

### 成功的交易鉴证

```
✅ 交易鉴证成功

交易令牌: eyJhbGciOiJIUzI1NiIs...
过期时间: 2024-01-01T10:05:00Z
设备状态: ACTIVE
安全评分: 95

💡 令牌已保存，可以进行交易处理
```

### 成功的交易处理

```
✅ 交易处理成功

交易ID: txn-123456
状态: SUCCESS
处理时间: 2024-01-01T10:01:00Z

💡 令牌已使用并清除
```

## 常见问题

### Q: 点击 "2. 交易处理" 提示 "没有可用的交易令牌"

**A:** 需要先点击 "1. 交易鉴证" 获取令牌。

### Q: 交易鉴证失败，提示 "Device not active"

**A:** 设备需要先审批。可以：
1. 使用后端管理界面审批设备
2. 或使用前端管理界面（sunbay-softpos-frontend）审批

### Q: 交易处理失败，提示 "Token expired"

**A:** 令牌有效期为 5 分钟，需要重新进行交易鉴证。

### Q: 如何审批设备？

**A:** 有两种方式：

**方式 1：使用 curl 命令**
```bash
# 获取 device_id（从应用日志中）
DEVICE_ID="dev-xxx"

# 登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.access_token')

# 审批设备
curl -X POST http://localhost:8080/api/v1/devices/$DEVICE_ID/approve \
  -H "Authorization: Bearer $TOKEN"
```

**方式 2：使用前端管理界面**
```bash
cd sunbay-softpos-frontend
npm start
# 访问 http://localhost:3000
# 登录后在设备管理页面审批
```

## 技术架构

```
┌─────────────────┐
│  Android App    │
│  (Kotlin)       │
└────────┬────────┘
         │ HTTP/JSON
         ▼
┌─────────────────┐
│  Backend API    │
│  (Rust/Actix)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  PostgreSQL     │
│  Redis          │
└─────────────────┘
```

### 交易流程

```
设备端                          后端
  │                              │
  │ 1. POST /transactions/attest │
  │ ──────────────────────────> │
  │    (health_check data)       │
  │                              │ 验证健康状态
  │                              │ 计算安全评分
  │                              │ 生成 JWT 令牌
  │                              │
  │ <────────────────────────── │
  │    (transaction_token)       │
  │                              │
  │ 保存令牌                     │
  │                              │
  │ 2. POST /transactions/process│
  │ ──────────────────────────> │
  │    (token + card data)       │
  │                              │ 验证令牌
  │                              │ 检查设备状态
  │                              │ 处理交易
  │                              │ 标记令牌已使用
  │                              │
  │ <────────────────────────── │
  │    (transaction_id)          │
  │                              │
  │ 清除令牌                     │
  │                              │
```

## 文件说明

### 新增文件

```
sunbay-softpos-android/
├── app/src/main/java/com/sunbay/softpos/
│   ├── data/
│   │   └── TransactionTokenManager.kt    # 交易令牌管理器（新增）
│   ├── network/
│   │   └── BackendApi.kt                 # 新增交易 API 接口
│   ├── security/
│   │   └── ThreatDetector.kt             # 新增健康检查方法
│   └── MainActivity.kt                    # 新增交易演示 UI
├── TRANSACTION_TOKEN_DEMO_GUIDE.md        # 详细使用指南（新增）
└── TRANSACTION_TOKEN_DEMO_README.md       # 快速开始（本文件）
```

## 下一步

1. **查看详细文档**：[TRANSACTION_TOKEN_DEMO_GUIDE.md](./TRANSACTION_TOKEN_DEMO_GUIDE.md)
2. **了解交易令牌设计**：[../TRANSACTION_TOKEN_FLOW_ANALYSIS.md](../TRANSACTION_TOKEN_FLOW_ANALYSIS.md)
3. **查看后端 API**：[../sunbay-softpos-backend/API_DOCUMENTATION.md](../sunbay-softpos-backend/API_DOCUMENTATION.md)

## 演示视频

（待添加）

## 截图

（待添加）

---

**版本：** 1.0.0  
**更新日期：** 2024-01-01
