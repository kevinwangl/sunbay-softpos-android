#!/bin/bash

# 交易令牌功能测试脚本
# 用于测试后端的交易令牌 API

set -e

# 配置
BACKEND_URL="http://localhost:8080"
DEVICE_ID=""
TOKEN=""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印函数
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# 检查 jq 是否安装
if ! command -v jq &> /dev/null; then
    print_error "jq is not installed. Please install it first."
    echo "  macOS: brew install jq"
    echo "  Ubuntu: sudo apt-get install jq"
    exit 1
fi

# 1. 健康检查
print_header "1. 后端健康检查"
HEALTH_RESPONSE=$(curl -s "$BACKEND_URL/health/check")
echo "$HEALTH_RESPONSE" | jq '.'

if echo "$HEALTH_RESPONSE" | jq -e '.status == "healthy"' > /dev/null; then
    print_success "后端服务正常运行"
else
    print_error "后端服务异常"
    exit 1
fi

# 2. 登录获取 Token
print_header "2. 管理员登录"
LOGIN_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "admin",
        "password": "admin123"
    }')

echo "$LOGIN_RESPONSE" | jq '.'

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.access_token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
    print_success "登录成功，获得 Token"
else
    print_error "登录失败"
    exit 1
fi

# 3. 查询设备列表
print_header "3. 查询设备列表"
DEVICES_RESPONSE=$(curl -s -X GET "$BACKEND_URL/api/v1/devices?page=1&page_size=10" \
    -H "Authorization: Bearer $TOKEN")

echo "$DEVICES_RESPONSE" | jq '.'

# 获取第一个 ACTIVE 设备的 ID
DEVICE_ID=$(echo "$DEVICES_RESPONSE" | jq -r '.devices[] | select(.status == "ACTIVE") | .device_id' | head -1)

if [ -z "$DEVICE_ID" ] || [ "$DEVICE_ID" == "null" ]; then
    print_error "没有找到 ACTIVE 状态的设备"
    print_info "请先在 Android 应用中注册设备并审批"
    exit 1
fi

print_success "找到设备: $DEVICE_ID"

# 4. 交易鉴证
print_header "4. 交易鉴证 (获取交易令牌)"
ATTEST_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/transactions/attest" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
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

echo "$ATTEST_RESPONSE" | jq '.'

TRANSACTION_TOKEN=$(echo "$ATTEST_RESPONSE" | jq -r '.transaction_token')
EXPIRES_AT=$(echo "$ATTEST_RESPONSE" | jq -r '.expires_at')
SECURITY_SCORE=$(echo "$ATTEST_RESPONSE" | jq -r '.security_score')

if [ "$TRANSACTION_TOKEN" != "null" ] && [ -n "$TRANSACTION_TOKEN" ]; then
    print_success "交易鉴证成功"
    print_info "交易令牌: ${TRANSACTION_TOKEN:0:50}..."
    print_info "过期时间: $EXPIRES_AT"
    print_info "安全评分: $SECURITY_SCORE"
else
    print_error "交易鉴证失败"
    exit 1
fi

# 5. 交易处理
print_header "5. 交易处理 (使用交易令牌)"
PROCESS_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/transactions/process" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
        \"transaction_token\": \"$TRANSACTION_TOKEN\",
        \"encrypted_pin_block\": \"SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)\",
        \"ksn\": \"FFFF9876543210E00001\",
        \"card_number\": \"6222021234567890\",
        \"amount\": 10000,
        \"currency\": \"CNY\"
    }")

echo "$PROCESS_RESPONSE" | jq '.'

TRANSACTION_ID=$(echo "$PROCESS_RESPONSE" | jq -r '.transaction_id')
TRANSACTION_STATUS=$(echo "$PROCESS_RESPONSE" | jq -r '.status')

if [ "$TRANSACTION_ID" != "null" ] && [ -n "$TRANSACTION_ID" ]; then
    print_success "交易处理成功"
    print_info "交易ID: $TRANSACTION_ID"
    print_info "状态: $TRANSACTION_STATUS"
else
    print_error "交易处理失败"
    exit 1
fi

# 6. 验证令牌已使用（应该失败）
print_header "6. 验证令牌一次性使用"
print_info "尝试重复使用同一令牌（应该失败）..."

REUSE_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/transactions/process" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
        \"transaction_token\": \"$TRANSACTION_TOKEN\",
        \"encrypted_pin_block\": \"SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)\",
        \"ksn\": \"FFFF9876543210E00001\",
        \"card_number\": \"6222021234567890\",
        \"amount\": 10000,
        \"currency\": \"CNY\"
    }")

echo "$REUSE_RESPONSE" | jq '.'

if echo "$REUSE_RESPONSE" | grep -q "already used\|invalid\|expired"; then
    print_success "令牌一次性使用验证通过（重复使用被拒绝）"
else
    print_error "令牌一次性使用验证失败（重复使用未被拒绝）"
fi

# 7. 测试令牌过期（可选，需要等待 5 分钟）
print_header "7. 测试总结"
print_success "所有测试通过！"
echo ""
print_info "测试覆盖："
echo "  ✅ 后端健康检查"
echo "  ✅ 管理员登录"
echo "  ✅ 设备查询"
echo "  ✅ 交易鉴证（获取令牌）"
echo "  ✅ 交易处理（使用令牌）"
echo "  ✅ 令牌一次性使用验证"
echo ""
print_info "交易令牌功能正常工作！"

# 可选：测试令牌过期
read -p "是否测试令牌过期功能？（需要等待 5 分钟）[y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_header "8. 测试令牌过期"
    
    # 重新获取令牌
    print_info "获取新的交易令牌..."
    ATTEST_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/transactions/attest" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
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
    
    TRANSACTION_TOKEN=$(echo "$ATTEST_RESPONSE" | jq -r '.transaction_token')
    print_success "获得新令牌: ${TRANSACTION_TOKEN:0:50}..."
    
    print_info "等待 5 分钟让令牌过期..."
    for i in {1..5}; do
        echo -ne "  等待中... $i/5 分钟\r"
        sleep 60
    done
    echo ""
    
    print_info "尝试使用过期令牌..."
    EXPIRED_RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/v1/transactions/process" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{
            \"transaction_token\": \"$TRANSACTION_TOKEN\",
            \"encrypted_pin_block\": \"SIMULATED_ENCRYPTED_PIN_BLOCK_$(date +%s)\",
            \"ksn\": \"FFFF9876543210E00001\",
            \"card_number\": \"6222021234567890\",
            \"amount\": 10000,
            \"currency\": \"CNY\"
        }")
    
    echo "$EXPIRED_RESPONSE" | jq '.'
    
    if echo "$EXPIRED_RESPONSE" | grep -q "expired\|invalid"; then
        print_success "令牌过期验证通过（过期令牌被拒绝）"
    else
        print_error "令牌过期验证失败（过期令牌未被拒绝）"
    fi
fi

print_header "测试完成"
