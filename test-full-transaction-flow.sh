#!/bin/bash

# 完整的交易流程测试
# 1. 交易鉴证获取令牌
# 2. 使用令牌进行交易处理

BACKEND_URL="http://10.23.10.54:8080"
DEVICE_ID="${1:-9e180285-b015-4954-83e0-ab7338104c3e}"

echo "=========================================="
echo "完整交易流程测试"
echo "=========================================="
echo ""
echo "后端地址: $BACKEND_URL"
echo "设备ID: $DEVICE_ID"
echo ""

# 步骤1：交易鉴证
echo "步骤1：交易鉴证（获取交易令牌）"
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
TRANSACTION_TOKEN=$(echo "$ATTEST_BODY" | jq -r '.transaction_token // empty')

if [ -z "$TRANSACTION_TOKEN" ]; then
    echo "❌ 无法提取交易令牌"
    exit 1
fi

echo "✅ 交易鉴证成功"
echo "交易令牌: ${TRANSACTION_TOKEN:0:50}..."
echo ""

# 步骤2：交易处理
echo "步骤2：交易处理（使用交易令牌）"
echo "----------------------------------------"

PROCESS_REQUEST=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "transaction_type": "PAYMENT",
  "amount": 10000,
  "currency": "CNY",
  "encrypted_pin_block": "SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)",
  "ksn": "FFFF9876543210E00001",
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
