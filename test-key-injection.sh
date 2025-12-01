#!/bin/bash

# 测试密钥注入功能
echo "=== 测试密钥注入功能 ==="
echo ""

# 后端地址
BACKEND_URL="http://10.23.10.54:8080"

# 测试设备ID
DEVICE_ID="90655c07-88bf-44fc-8ec4-203eee297ae0"

echo "1. 测试密钥注入端点"
echo "   POST ${BACKEND_URL}/api/v1/keys/inject"
echo ""

curl -X POST "${BACKEND_URL}/api/v1/keys/inject" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\": \"${DEVICE_ID}\"}" \
  -w "\n\nHTTP Status: %{http_code}\n" \
  -s | jq '.' 2>/dev/null || cat

echo ""
echo "=== 测试完成 ==="
echo ""
echo "修复内容："
echo "1. ✅ 修复了请求字段名：device_id -> deviceId"
echo "2. ✅ 更新了响应模型以匹配后端返回的字段"
echo "3. ✅ 添加了网络超时配置（30秒）"
echo ""
echo "现在重新编译Android应用并测试密钥注入功能"
