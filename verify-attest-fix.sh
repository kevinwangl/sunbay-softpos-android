#!/bin/bash

# 验证交易鉴证修复
# 测试移除 health_check 字段后的请求

BACKEND_URL="http://10.23.10.54:8080"
DEVICE_ID="${1:-9e180285-b015-4954-83e0-ab7338104c3e}"

echo "=========================================="
echo "验证交易鉴证 API 修复"
echo "=========================================="
echo ""
echo "后端地址: $BACKEND_URL"
echo "设备ID: $DEVICE_ID"
echo ""

# 步骤0：登录获取 token
echo "步骤0：登录获取访问令牌..."
echo "----------------------------------------"
LOGIN_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.access_token // empty')

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败，无法获取访问令牌"
    echo "$LOGIN_RESPONSE" | jq .
    exit 1
fi

echo "✅ 登录成功"
echo ""

# 步骤1：检查设备状态
echo "步骤1：检查设备状态..."
echo "----------------------------------------"
DEVICE_INFO=$(curl -s "$BACKEND_URL/api/v1/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $TOKEN")

echo "设备信息响应:"
echo "$DEVICE_INFO" | jq .
echo ""

DEVICE_STATUS=$(echo "$DEVICE_INFO" | jq -r '.status // "unknown"')
DEVICE_MODE=$(echo "$DEVICE_INFO" | jq -r '.device_mode // "unknown"')
KEY_INJECTED=$(echo "$DEVICE_INFO" | jq -r '.ipek_injected_at // "null"')

echo "设备状态: $DEVICE_STATUS"
echo "设备模式: $DEVICE_MODE"
echo "密钥注入: $KEY_INJECTED"
echo ""

if [ "$DEVICE_STATUS" != "Active" ]; then
    echo "⚠️  设备状态不是 Active: $DEVICE_STATUS"
    echo "   继续测试交易鉴证 API（可能会失败）"
    echo ""
fi

if [ "$DEVICE_MODE" != "FullPos" ] && [ "$DEVICE_MODE" != "unknown" ]; then
    echo "⚠️  设备模式不是 FullPos: $DEVICE_MODE"
    echo "   继续测试交易鉴证 API（可能会失败）"
    echo ""
fi

if [ "$KEY_INJECTED" = "null" ]; then
    echo "⚠️  设备未注入密钥"
    echo "   继续测试交易鉴证 API（可能会失败）"
    echo ""
fi

# 步骤2：测试交易鉴证（新格式，不包含 health_check）
echo "步骤2：测试交易鉴证（新格式）..."
echo "----------------------------------------"

REQUEST_BODY=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "amount": 10000,
  "currency": "CNY"
}
EOF
)

echo "请求体:"
echo "$REQUEST_BODY" | jq .
echo ""

echo "发送请求..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BACKEND_URL/api/v1/transactions/attest" \
  -H "Content-Type: application/json" \
  -d "$REQUEST_BODY")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo ""
echo "HTTP 状态码: $HTTP_CODE"
echo ""
echo "响应体:"
echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ 交易鉴证成功！"
    echo ""
    
    # 提取交易令牌信息
    TOKEN=$(echo "$BODY" | jq -r '.transaction_token // empty')
    EXPIRES_AT=$(echo "$BODY" | jq -r '.expires_at // empty')
    
    if [ -n "$TOKEN" ]; then
        echo "交易令牌: ${TOKEN:0:50}..."
        echo "过期时间: $EXPIRES_AT"
        echo ""
        echo "💡 现在可以使用此令牌进行交易处理"
    fi
else
    echo "❌ 交易鉴证失败"
    echo ""
    echo "错误分析:"
    ERROR_MSG=$(echo "$BODY" | jq -r '.error_message // .message // empty')
    if [ -n "$ERROR_MSG" ]; then
        echo "  错误信息: $ERROR_MSG"
    fi
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
