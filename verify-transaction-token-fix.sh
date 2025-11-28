#!/bin/bash

# 验证交易令牌 401 错误修复

set -e

BACKEND_URL="http://localhost:8080"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}验证交易令牌 401 错误修复${NC}"
echo -e "${YELLOW}========================================${NC}\n"

# 1. 检查后端健康
echo -e "${YELLOW}1. 检查后端服务...${NC}"
if curl -s "$BACKEND_URL/health/check" > /dev/null; then
    echo -e "${GREEN}✅ 后端服务正常${NC}\n"
else
    echo -e "${RED}❌ 后端服务未运行${NC}"
    echo "请先启动后端: cd sunbay-softpos-backend && cargo run --release"
    exit 1
fi

# 2. 获取设备 ID
echo -e "${YELLOW}2. 获取测试设备...${NC}"

# 登录获取 token
TOKEN=$(curl -s -X POST "$BACKEND_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
    | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
    echo -e "${RED}❌ 登录失败${NC}"
    exit 1
fi

# 获取第一个 ACTIVE 设备
DEVICE_ID=$(curl -s -X GET "$BACKEND_URL/api/v1/devices?page=1&page_size=10" \
    -H "Authorization: Bearer $TOKEN" \
    | jq -r '.devices[] | select(.status == "ACTIVE") | .device_id' | head -1)

if [ -z "$DEVICE_ID" ] || [ "$DEVICE_ID" == "null" ]; then
    echo -e "${RED}❌ 没有找到 ACTIVE 设备${NC}"
    echo "请先在 Android 应用中注册设备并审批"
    exit 1
fi

echo -e "${GREEN}✅ 找到设备: $DEVICE_ID${NC}\n"

# 3. 测试交易鉴证（不带 Authorization header）
echo -e "${YELLOW}3. 测试交易鉴证接口（无认证）...${NC}"

ATTEST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/transactions/attest" \
    -H "Content-Type: application/json" \
    -d "{
        \"device_id\": \"$DEVICE_ID\",
        \"amount\": 10000,
        \"currency\": \"CNY\",
        \"health_check\": {
            \"root_status\": false,
            \"debug_status\": false,
            \"hook_status\": false,
            \"emulator_status\": false,
            \"tee_status\": true,
            \"system_integrity\": true,
            \"app_integrity\": true
        }
    }")

HTTP_CODE=$(echo "$ATTEST_RESPONSE" | tail -1)
RESPONSE_BODY=$(echo "$ATTEST_RESPONSE" | head -n -1)

echo "HTTP 状态码: $HTTP_CODE"
echo "响应内容:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
echo ""

if [ "$HTTP_CODE" == "200" ]; then
    echo -e "${GREEN}✅ 交易鉴证成功（无需认证）${NC}"
    
    # 提取交易令牌
    TRANSACTION_TOKEN=$(echo "$RESPONSE_BODY" | jq -r '.transaction_token')
    
    if [ -n "$TRANSACTION_TOKEN" ] && [ "$TRANSACTION_TOKEN" != "null" ]; then
        echo -e "${GREEN}✅ 获得交易令牌: ${TRANSACTION_TOKEN:0:50}...${NC}\n"
        
        # 4. 测试交易处理（不带 Authorization header）
        echo -e "${YELLOW}4. 测试交易处理接口（无认证）...${NC}"
        
        PROCESS_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/transactions/process" \
            -H "Content-Type: application/json" \
            -d "{
                \"transaction_token\": \"$TRANSACTION_TOKEN\",
                \"encrypted_pin_block\": \"SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)\",
                \"ksn\": \"FFFF9876543210E00001\",
                \"card_number\": \"6222021234567890\",
                \"amount\": 10000,
                \"currency\": \"CNY\",
                \"device_id\": \"$DEVICE_ID\"
            }")
        
        HTTP_CODE=$(echo "$PROCESS_RESPONSE" | tail -1)
        RESPONSE_BODY=$(echo "$PROCESS_RESPONSE" | head -n -1)
        
        echo "HTTP 状态码: $HTTP_CODE"
        echo "响应内容:"
        echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
        echo ""
        
        if [ "$HTTP_CODE" == "200" ]; then
            echo -e "${GREEN}✅ 交易处理成功（无需认证）${NC}\n"
            
            TRANSACTION_ID=$(echo "$RESPONSE_BODY" | jq -r '.transaction_id')
            echo -e "${GREEN}✅ 交易ID: $TRANSACTION_ID${NC}\n"
        else
            echo -e "${RED}❌ 交易处理失败: HTTP $HTTP_CODE${NC}\n"
            exit 1
        fi
    else
        echo -e "${RED}❌ 未获得交易令牌${NC}\n"
        exit 1
    fi
elif [ "$HTTP_CODE" == "401" ]; then
    echo -e "${RED}❌ 仍然返回 401 错误${NC}"
    echo -e "${RED}请确保后端已重新编译并重启${NC}\n"
    exit 1
else
    echo -e "${RED}❌ 交易鉴证失败: HTTP $HTTP_CODE${NC}\n"
    exit 1
fi

# 5. 总结
echo -e "${YELLOW}========================================${NC}"
echo -e "${GREEN}✅ 所有测试通过！${NC}"
echo -e "${YELLOW}========================================${NC}\n"

echo "测试结果："
echo "  ✅ 后端服务正常"
echo "  ✅ 交易鉴证接口无需认证"
echo "  ✅ 交易处理接口无需认证"
echo "  ✅ 完整交易流程正常"
echo ""
echo -e "${GREEN}401 错误已修复！Android 应用现在可以正常调用交易接口。${NC}"
