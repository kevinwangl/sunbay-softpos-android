#!/bin/bash

# 调试交易鉴证请求
# 使用 curl 模拟 Android 端发送的请求

BACKEND_URL="http://10.23.10.54:8080"
DEVICE_ID="9e180285-b015-4954-83e0-ab7338104c3e"

echo "=== 测试交易鉴证 API ==="
echo "后端地址: $BACKEND_URL"
echo "设备ID: $DEVICE_ID"
echo ""

# 构建请求体
REQUEST_BODY=$(cat <<EOF
{
  "device_id": "$DEVICE_ID",
  "amount": 10000,
  "currency": "CNY",
  "health_check": {
    "root_status": false,
    "debug_status": false,
    "hook_status": false,
    "emulator_status": false,
    "tee_status": true,
    "system_integrity": true,
    "app_integrity": true
  }
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
    echo "✅ 交易鉴证成功"
else
    echo "❌ 交易鉴证失败"
    echo ""
    echo "可能的原因:"
    echo "1. 设备未注册或未审批"
    echo "2. 设备状态不是 Active"
    echo "3. 请求体格式错误"
    echo ""
    echo "检查设备状态:"
    curl -s "$BACKEND_URL/api/v1/devices/$DEVICE_ID" | jq .
fi
