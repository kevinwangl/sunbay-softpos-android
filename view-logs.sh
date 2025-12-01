#!/bin/bash

# 查看 Android 应用的文件日志
# 这个脚本会从设备中拉取日志文件到本地

echo "========================================="
echo "SoftPOS Android 日志查看工具"
echo "========================================="
echo ""

# 获取应用包名
PACKAGE_NAME="com.sunbay.softpos"

# 获取日志目录路径
LOG_DIR="/sdcard/Android/data/${PACKAGE_NAME}/files/softpos_logs"

echo "1. 检查设备连接..."
adb devices

echo ""
echo "2. 列出日志文件..."
adb shell "ls -lh ${LOG_DIR}" 2>/dev/null || {
    echo "❌ 日志目录不存在或无法访问"
    echo "请确保："
    echo "  - 应用已运行过"
    echo "  - FileLogger.init() 已被调用"
    exit 1
}

echo ""
echo "3. 拉取最新的日志文件..."
LATEST_LOG=$(adb shell "ls -t ${LOG_DIR}/*.log | head -1" | tr -d '\r')

if [ -z "$LATEST_LOG" ]; then
    echo "❌ 没有找到日志文件"
    exit 1
fi

echo "最新日志文件: $LATEST_LOG"

# 创建本地日志目录
mkdir -p ./android_logs

# 拉取日志文件
LOCAL_LOG="./android_logs/$(basename $LATEST_LOG)"
adb pull "$LATEST_LOG" "$LOCAL_LOG"

echo ""
echo "========================================="
echo "✅ 日志文件已保存到: $LOCAL_LOG"
echo "========================================="
echo ""

# 显示最后50行
echo "📄 最后50行日志内容:"
echo "========================================="
tail -50 "$LOCAL_LOG"
echo "========================================="

echo ""
echo "💡 提示:"
echo "  - 查看完整日志: cat $LOCAL_LOG"
echo "  - 搜索错误: grep ERROR $LOCAL_LOG"
echo "  - 搜索400错误: grep '400' $LOCAL_LOG"
echo "  - 实时查看: tail -f $LOCAL_LOG (需要持续拉取)"
echo ""
