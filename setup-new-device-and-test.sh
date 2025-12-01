#!/bin/bash

# 注册新设备并完成完整的交易测试流程

BACKEND_URL="http://10.23.10.54:8080"
NEW_IMEI="$(date +%s)12345"  # 使用时间戳生成唯一的 IMEI

echo "=========================================="
echo "注册新设备并测试完整交易流程"
echo "=========================================="
echo ""
echo "后端地址: $BACKEND_URL"
echo "新设备 IMEI: $NEW_IMEI"
echo ""

# 步骤1：注册新设备
echo "步骤1：注册新设备"
echo "----------------------------------------"

REGISTER_REQUEST=$(cat <<EOF
{
  "imei": "$NEW_IMEI",
  "model": "TestDevice",
  "os_version": "Android 13",
  "tee_type": "TRUST_ZONE",
  "public_key": "test_public_key_$(date +%s)",
  "device_mode": "FULL_POS",
  "nfc_present": true
}
EOF
)

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/devices/register" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_REQUEST")

REGISTER_HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

echo "HTTP 状态码: $REGISTER_HTTP_CODE"
echo "响应体:"
echo "$REGISTER_BODY" | jq . 2>/dev/null || echo "$REGISTER_BODY"
echo ""

if [ "$REGISTER_HTTP_CODE" != "200" ] && [ "$REGISTER_HTTP_CODE" != "201" ]; then
    echo "❌ 设备注册失败"
    exit 1
fi

DEVICE_ID=$(echo "$REGISTER_BODY" | jq -r '.data.device_id // .device_id // empty')

if [ -z "$DEVICE_ID" ]; then
    echo "❌ 无法提取设备 ID"
    exit 1
fi

echo "✅ 设备注册成功"
echo "设备 ID: $DEVICE_ID"
echo ""

# 步骤2：登录获取管理员 token
echo "步骤2：登录获取管理员令牌"
echo "----------------------------------------"

LOGIN_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.token // .data.access_token // empty')

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败"
    exit 1
fi

echo "✅ 登录成功"
echo ""

# 步骤3：审批设备
echo "步骤3：审批设备"
echo "----------------------------------------"

APPROVE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/devices/$DEVICE_ID/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"device_id":"'$DEVICE_ID'","operator":"admin"}')

APPROVE_HTTP_CODE=$(echo "$APPROVE_RESPONSE" | tail -n1)
APPROVE_BODY=$(echo "$APPROVE_RESPONSE" | sed '$d')

echo "HTTP 状态码: $APPROVE_HTTP_CODE"
echo "响应体:"
echo "$APPROVE_BODY" | jq . 2>/dev/null || echo "$APPROVE_BODY"
echo ""

if [ "$APPROVE_HTTP_CODE" != "200" ]; then
    echo "❌ 设备审批失败"
    exit 1
fi

echo "✅ 设备审批成功"
echo ""

# 步骤4：注入密钥
echo "步骤4：注入密钥"
echo "----------------------------------------"

INJECT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/keys/inject" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"'$DEVICE_ID'"}')

INJECT_HTTP_CODE=$(echo "$INJECT_RESPONSE" | tail -n1)
INJECT_BODY=$(echo "$INJECT_RESPONSE" | sed '$d')

echo "HTTP 状态码: $INJECT_HTTP_CODE"
echo "响应体:"
echo "$INJECT_BODY" | jq . 2>/dev/null || echo "$INJECT_BODY"
echo ""

if [ "$INJECT_HTTP_CODE" != "200" ]; then
    echo "❌ 密钥注入失败"
    exit 1
fi

echo "✅ 密钥注入成功"
echo ""

# 从注入响应中提取 KSN
CURRENT_KSN=$(echo "$INJECT_BODY" | jq -r '.ksn // empty')

if [ -z "$CURRENT_KSN" ]; then
    echo "❌ 无法从注入响应中提取 KSN"
    exit 1
fi

echo "设备 KSN: $CURRENT_KSN"
echo ""

# 步骤5：交易鉴证
echo "步骤5：交易鉴证"
echo "----------------------------------------"

ATTEST_REQUEST=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "amount": 10000,
  "currency": "CNY"
}
EOF
)

ATTEST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/transactions/attest" \
  -H "Content-Type: application/json" \
  -d "$ATTEST_REQUEST")

ATTEST_HTTP_CODE=$(echo "$ATTEST_RESPONSE" | tail -n1)
ATTEST_BODY=$(echo "$ATTEST_RESPONSE" | sed '$d')

echo "HTTP 状态码: $ATTEST_HTTP_CODE"
echo "响应体:"
echo "$ATTEST_BODY" | jq . 2>/dev/null || echo "$ATTEST_BODY"
echo ""

if [ "$ATTEST_HTTP_CODE" != "200" ]; then
    echo "❌ 交易鉴证失败"
    exit 1
fi

TRANSACTION_TOKEN=$(echo "$ATTEST_BODY" | jq -r '.transaction_token // .token // empty')

if [ -z "$TRANSACTION_TOKEN" ]; then
    echo "❌ 无法提取交易令牌"
    exit 1
fi

echo "✅ 交易鉴证成功"
echo "交易令牌: ${TRANSACTION_TOKEN:0:50}..."
echo ""

# 步骤6：交易处理
echo "步骤6：交易处理"
echo "----------------------------------------"

PROCESS_REQUEST=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "transaction_type": "PAYMENT",
  "amount": 10000,
  "currency": "CNY",
  "encrypted_pin_block": "SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)",
  "ksn": "$CURRENT_KSN",
  "card_number_masked": "622202******7890",
  "transaction_token": "$TRANSACTION_TOKEN"
}
EOF
)

PROCESS_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/transactions/process" \
  -H "Content-Type: application/json" \
  -d "$PROCESS_REQUEST")

PROCESS_HTTP_CODE=$(echo "$PROCESS_RESPONSE" | tail -n1)
PROCESS_BODY=$(echo "$PROCESS_RESPONSE" | sed '$d')

echo "HTTP 状态码: $PROCESS_HTTP_CODE"
echo "响应体:"
echo "$PROCESS_BODY" | jq . 2>/dev/null || echo "$PROCESS_BODY"
echo ""

if [ "$PROCESS_HTTP_CODE" = "200" ]; then
    echo "✅ 交易处理成功！"
    echo ""
    TRANSACTION_ID=$(echo "$PROCESS_BODY" | jq -r '.transaction_id // empty')
    if [ -n "$TRANSACTION_ID" ]; then
        echo "交易ID: $TRANSACTION_ID"
    fi
    echo ""
    echo "=========================================="
    echo "完整流程测试成功！"
    echo "=========================================="
    echo ""
    echo "新设备信息："
    echo "  设备 ID: $DEVICE_ID"
    echo "  IMEI: $NEW_IMEI"
    echo "  KSN: $CURRENT_KSN"
else
    echo "❌ 交易处理失败"
    ERROR_MSG=$(echo "$PROCESS_BODY" | jq -r '.error_message // .message // empty')
    if [ -n "$ERROR_MSG" ]; then
        echo "错误信息: $ERROR_MSG"
    fi
fi
