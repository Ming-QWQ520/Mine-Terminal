#!/bin/bash
# ============================================================================
# 打包 mine-terminal 的 fat-jar
#   - mine-terminal 自身 22 个 class
#   - mods.toml / pack.mcmeta / 资源
#   - pty4j（含跨平台原生库）
#   - jediterm-core + jediterm-pty
# 注意：不含 Minecraft/Forge（这些由 Minecraft 客户端在运行时提供）
# ============================================================================
set -e

export JAVA_HOME="$HOME/tools/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="/home/z/my-project/mine-terminal"
STAGE_DIR="$PROJECT_DIR/build/fat-stage"
FAT_JAR="$PROJECT_DIR/build/libs/mineterm-0.1.0-fat.jar"

rm -rf "$STAGE_DIR" "$FAT_JAR"
mkdir -p "$STAGE_DIR/META-INF"

echo "[1/5] 拷贝 mine-terminal 编译产物..."
cp -r "$PROJECT_DIR/build/classes/com" "$STAGE_DIR/"
# 拷贝资源文件（mods.toml、lang 等）
cp "$PROJECT_DIR/src/main/resources/META-INF/mods.toml" "$STAGE_DIR/META-INF/"
cp "$PROJECT_DIR/src/main/resources/pack.mcmeta" "$STAGE_DIR/"
cp -r "$PROJECT_DIR/src/main/resources/assets" "$STAGE_DIR/"

echo "[2/5] 解包并合并 pty4j（含原生库）..."
mkdir -p "$STAGE_DIR/tmp-pty4j"
(cd "$STAGE_DIR/tmp-pty4j" && jar xf "$HOME/tools/libs/pty4j.jar")
# 把 pty4j 的 class 与 native 库合并到 stage，但要避免覆盖我们的 mods.toml
cp -rn "$STAGE_DIR/tmp-pty4j/com" "$STAGE_DIR/" 2>/dev/null || true
# pty4j 的原生库在 /com/pty4j/native 等路径下，会随之被拷贝
rm -rf "$STAGE_DIR/tmp-pty4j"

echo "[3/5] 解包并合并 jediterm-core..."
mkdir -p "$STAGE_DIR/tmp-jedi"
(cd "$STAGE_DIR/tmp-jedi" && jar xf "$HOME/tools/libs/jediterm-core.jar")
cp -rn "$STAGE_DIR/tmp-jedi/com" "$STAGE_DIR/" 2>/dev/null || true
rm -rf "$STAGE_DIR/tmp-jedi"

echo "[4/5] 解包并合并 jediterm-pty..."
mkdir -p "$STAGE_DIR/tmp-jedip"
(cd "$STAGE_DIR/tmp-jedip" && jar xf "$HOME/tools/libs/jediterm-pty.jar")
cp -rn "$STAGE_DIR/tmp-jedip/com" "$STAGE_DIR/" 2>/dev/null || true
rm -rf "$STAGE_DIR/tmp-jedip"

# 也要把 kotlin 标准库的元数据（jediterm 用 kotlin 写的）一并带上
# 这部分可选——如果 jediterm-core 依赖 kotlin-stdlib，需要单独下载并合并
# 检查是否需要 kotlin-stdlib
echo "[4.5/5] 检查 kotlin 依赖..."
KOTLIN_URL="https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.8.10/kotlin-stdlib-1.8.10.jar"
curl -fsSL -o /tmp/kotlin-stdlib.jar "$KOTLIN_URL" 2>&1 | tail -3
if [ -f /tmp/kotlin-stdlib.jar ]; then
    mkdir -p "$STAGE_DIR/tmp-kotlin"
    (cd "$STAGE_DIR/tmp-kotlin" && jar xf /tmp/kotlin-stdlib.jar)
    # kotlin 的 class 在 kotlin/ 目录下，与 com/ 不冲突
    cp -rn "$STAGE_DIR/tmp-kotlin/kotlin" "$STAGE_DIR/" 2>/dev/null || true
    cp -rn "$STAGE_DIR/tmp-kotlin/META-INF" "$STAGE_DIR/" 2>/dev/null || true
    rm -rf "$STAGE_DIR/tmp-kotlin"
    rm /tmp/kotlin-stdlib.jar
    echo "  kotlin-stdlib merged"
fi

echo "[5/5] 创建 fat-jar..."
cat > "$STAGE_DIR/META-INF/MANIFEST.MF" << 'EOF'
Manifest-Version: 1.0
Specification-Title: mineterm
Specification-Vendor: mine-terminal-dev
Specification-Version: 1
Implementation-Title: mine-terminal
Implementation-Version: 0.1.0
Implementation-Vendor: mine-terminal-dev
Implementation-Timestamp: 2026-07-11
Class-Path: 
EOF

(cd "$STAGE_DIR" && jar cfm "$FAT_JAR" META-INF/MANIFEST.MF -C . .)

echo '---'
ls -lh "$FAT_JAR"
echo "=== fat-jar 顶层条目 ==="
jar tf "$FAT_JAR" | head -20
echo "=== 总条目数 ==="
jar tf "$FAT_JAR" | wc -l
echo "=== 原生库 ==="
jar tf "$FAT_JAR" | grep -E '\.(so|dll|dylib)$' | head -10