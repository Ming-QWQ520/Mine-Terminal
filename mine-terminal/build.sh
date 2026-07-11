#!/usr/bin/env bash
# ============================================================================
#  Mine-Terminal — 构建辅助脚本
#  用途：
#    1) 若本机已装 gradle，则生成 gradle wrapper
#    2) 调用 gradle wrapper 执行 build / runClient / clean
#
#  使用方式：
#    ./build.sh            # 默认 build
#    ./build.sh build      # 构建
#    ./build.sh runClient  # 启动客户端测试
#    ./build.sh clean      # 清理
#    ./build.sh wrapper    # 仅生成 gradle wrapper
# ============================================================================
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# ---------- 1. 找到 gradle ----------
GRADLE_BIN="$(command -v gradle 2>/dev/null || true)"

if [ -z "$GRADLE_BIN" ]; then
    echo "[ERROR] 未找到 gradle。"
    echo "  请先安装 gradle 7.6+ 或从 Forge MDK 复制 gradlew / gradlew.bat / gradle/wrapper/gradle-wrapper.jar"
    echo "  Forge MDK: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html"
    exit 1
fi

# ---------- 2. 生成 wrapper（如果还没有） ----------
if [ ! -f "./gradlew" ]; then
    echo "[INFO] Generating gradle wrapper..."
    gradle wrapper --gradle-version 8.1.1 --distribution-type bin
fi

# ---------- 3. 执行任务 ----------
TASK="${1:-build}"
echo "[INFO] Running: ./gradlew $TASK"
./gradlew "$TASK"
