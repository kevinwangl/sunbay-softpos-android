#!/bin/bash

# 完整的交易流程测试（包括密钥注入）
# 1. 登录获取 token
# 2. 注入密钥
# 3. 交易鉴证获取令牌
# 4. 使用令牌进行交易处理

BACKEND_URL="http://10.23.10.54:8080"
DEVICE_ID="${1:-9e180285-b015-4954-83e0-ab7338104c3e}"

echo "=========================================="
echo "完整交易流程测试（含密钥注入）"
echo "=========================================="
echo ""
echo "后端地址: $BACKEND_URL"
echo "设备ID: $DEVICE_ID"
echo ""

# 步骤0：登录获取 token
echo "步骤0：登录获取访问令牌"
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

# 步骤1：注入密钥
echo "步骤1：注入密钥"
echo "----------------------------------------"

INJECT_REQUEST='{"deviceId":"'$DEVICE_ID'"}'

INJECT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/keys/inject" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$INJECT_REQUEST")

INJECT_HTTP_CODE=$(echo "$INJECT_RESPONSE" | tail -n1)
INJECT_BODY=$(echo "$INJECT_RESPONSE" | sed '$d')

echo "HTTP 状态码: $INJECT_HTTP_CODE"
echo "响应体:"
echo "$INJECT_BODY" | jq . 2>/dev/null || echo "$INJECT_BODY"
echo ""

if [ "$INJECT_HTTP_CODE" != "200" ]; then
    echo "⚠️  密钥注入失败，可能已经注入过了，继续测试..."
    echo ""
else
    echo "✅ 密钥注入成功"
    echo ""
    
    # 等待一下确保数据库更新
    sleep 1
fi

# 验证密钥是否已注入
echo "验证密钥状态..."
DEVICE_INFO=$(curl -s "$BACKEND_URL/api/v1/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $TOKEN")

CURRENT_KSN=$(echo "$DEVICE_INFO" | jq -r '.current_ksn // "null"')
echo "当前 KSN: $CURRENT_KSN"
echo ""

if [ "$CURRENT_KSN" = "null" ]; then
    echo "❌ 设备密钥未注入，无法继续"
    exit 1
fi

# 步骤2：交易鉴证
echo "步骤2：交易鉴证（获取交易令牌）"
echo "----------------------------------------"

ATTEST_REQUEST=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "amount": 10000,
  "currency": "CNY"
}
EOF
)

echo "请求体:"
echo "$ATTEST_REQUEST" | jq .
echo ""

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
    echo "❌ 交易鉴证失败，无法继续"
    exit 1
fi

# 提取交易令牌
TRANSACTION_TOKEN=$(echo "$ATTEST_BODY" | jq -r '.transaction_token // .token // empty')

if [ -z "$TRANSACTION_TOKEN" ]; then
    echo "❌ 无法提取交易令牌"
    exit 1
fi

echo "✅ 交易鉴证成功"
echo "交易令牌: ${TRANSACTION_TOKEN:0:50}..."
echo ""

# 步骤3：交易处理
echo "步骤3：交易处理（使用交易令牌）"
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

echo "请求体:"
echo "$PROCESS_REQUEST" | jq .
echo ""

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
    
    TRANSACTION_ID=$(echo "$PROCESS_BODY" | jq -r '.transaction_id // empty')
    if [ -n "$TRANSACTION_ID" ]; then
        echo "交易ID: $TRANSACTION_ID"
    fi
else
    echo "❌ 交易处理失败"
    echo ""
    echo "错误分析:"
    ERROR_MSG=$(echo "$PROCESS_BODY" | jq -r '.error_message // .message // empty')
    if [ -n "$ERROR_MSG" ]; then
        echo "  错误信息: $ERROR_MSG"
    fi
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
