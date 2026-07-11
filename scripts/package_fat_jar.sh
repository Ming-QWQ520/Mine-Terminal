#!/bin/bash
# ============================================================================
# 重新打包 fat-jar，使用已替换占位符的资源文件
# ============================================================================
set -e

export JAVA_HOME="$HOME/tools/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="/home/z/my-project/mine-terminal"
STAGE_DIR="$PROJECT_DIR/build/fat-stage"
FAT_JAR="$PROJECT_DIR/build/libs/mineterm-0.1.0-fat.jar"

rm -rf "$STAGE_DIR" "$FAT_JAR"
mkdir -p "$STAGE_DIR/META-INF" "$PROJECT_DIR/build/libs"

echo "[1/6] 拷贝 mine-terminal 编译产物（class）..."
cp -r "$PROJECT_DIR/build/classes/com" "$STAGE_DIR/"

echo "[2/6] 拷贝已替换占位符的资源文件..."
cp "$PROJECT_DIR/build/processed-resources/META-INF/mods.toml" "$STAGE_DIR/META-INF/"
cp "$PROJECT_DIR/build/processed-resources/pack.mcmeta" "$STAGE_DIR/"
cp -r "$PROJECT_DIR/build/processed-resources/assets" "$STAGE_DIR/"

echo "[3/6] 合并 pty4j（含跨平台原生库）..."
mkdir -p "$STAGE_DIR/tmp-pty4j"
(cd "$STAGE_DIR/tmp-pty4j" && jar xf "$HOME/tools/libs/pty4j.jar")
# pty4j 的 class
cp -rn "$STAGE_DIR/tmp-pty4j/com" "$STAGE_DIR/" 2>/dev/null || true
# pty4j 的原生库（resources/ 目录）
cp -rn "$STAGE_DIR/tmp-pty4j/resources" "$STAGE_DIR/" 2>/dev/null || true
rm -rf "$STAGE_DIR/tmp-pty4j"

echo "[4/6] 合并 jediterm-core..."
mkdir -p "$STAGE_DIR/tmp-jedi"
(cd "$STAGE_DIR/tmp-jedi" && jar xf "$HOME/tools/libs/jediterm-core.jar")
cp -rn "$STAGE_DIR/tmp-jedi/com" "$STAGE_DIR/" 2>/dev/null || true
cp -rn "$STAGE_DIR/tmp-jedi/META-INF" "$STAGE_DIR/tmp-jedi-merge" 2>/dev/null || true
rm -rf "$STAGE_DIR/tmp-jedi"

echo "[5/6] 合并 jediterm-pty + kotlin-stdlib..."
mkdir -p "$STAGE_DIR/tmp-jedip"
(cd "$STAGE_DIR/tmp-jedip" && jar xf "$HOME/tools/libs/jediterm-pty.jar")
cp -rn "$STAGE_DIR/tmp-jedip/com" "$STAGE_DIR/" 2>/dev/null || true
rm -rf "$STAGE_DIR/tmp-jedip"

# kotlin-stdlib
KOTLIN_URL="https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.8.10/kotlin-stdlib-1.8.10.jar"
if [ ! -f /tmp/kotlin-stdlib.jar ]; then
    curl -fsSL -o /tmp/kotlin-stdlib.jar "$KOTLIN_URL"
fi
if [ -f /tmp/kotlin-stdlib.jar ]; then
    mkdir -p "$STAGE_DIR/tmp-kotlin"
    (cd "$STAGE_DIR/tmp-kotlin" && jar xf /tmp/kotlin-stdlib.jar)
    cp -rn "$STAGE_DIR/tmp-kotlin/kotlin" "$STAGE_DIR/" 2>/dev/null || true
    # 注意：不要覆盖我们的 META-INF/MANIFEST.MF 和 mods.toml
    # kotlin 的 META-INF 只保留 services 子目录（如有）
    if [ -d "$STAGE_DIR/tmp-kotlin/META-INF/services" ]; then
        mkdir -p "$STAGE_DIR/META-INF/services"
        cp -rn "$STAGE_DIR/tmp-kotlin/META-INF/services/." "$STAGE_DIR/META-INF/services/" 2>/dev/null || true
    fi
    rm -rf "$STAGE_DIR/tmp-kotlin"
    echo "  kotlin-stdlib merged"
fi

echo "[6/6] 创建 fat-jar..."
cat > "$STAGE_DIR/META-INF/MANIFEST.MF" << 'EOF'
Manifest-Version: 1.0
Specification-Title: mineterm
Specification-Vendor: mine-terminal-dev
Specification-Version: 1
Implementation-Title: mine-terminal
Implementation-Version: 0.1.0
Implementation-Vendor: mine-terminal-dev
Implementation-Timestamp: 2026-07-11
EOF

(cd "$STAGE_DIR" && jar cfm "$FAT_JAR" META-INF/MANIFEST.MF -C . .)

echo ''
echo '=== Fat JAR 已生成 ==='
ls -lh "$FAT_JAR"
