#!/usr/bin/env python3
"""
替换 mods.toml 与 pack.mcmeta 中的 ${...} 占位符。

模拟 ForgeGradle 的 processResources 行为：
  - 读取 gradle.properties 中的 key=value
  - 把 ${key} 替换为 value
  - 输出到 build/processed-resources/

替换后的文件用于打入 fat-jar。
"""
import re
import sys
from pathlib import Path

PROJECT = Path("/home/z/my-project/mine-terminal")
GRADLE_PROPS = PROJECT / "gradle.properties"
SRC_RES = PROJECT / "src" / "main" / "resources"
OUT_RES = PROJECT / "build" / "processed-resources"


def load_gradle_props():
    props = {}
    for line in GRADLE_PROPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    # 补充 build.gradle 中 processResources 定义的合成属性
    # 这些不在 gradle.properties 中，但是 mods.toml 引用了
    props.setdefault("minecraft_version_range", "[1.20.1,1.21)")
    props.setdefault("forge_version_range", "[47,)")
    props.setdefault("loader_version_range", "[47,)")
    return props


def substitute(text, props):
    # 反复替换直到没有 ${...}（支持嵌套，如 ${${x}}，虽然我们这里没有）
    pattern = re.compile(r"\$\{([^}]+)\}")
    for _ in range(5):
        new_text = pattern.sub(lambda m: props.get(m.group(1), m.group(0)), text)
        if new_text == text:
            break
        text = new_text
    return text


def main():
    props = load_gradle_props()
    print("Loaded gradle.properties:")
    for k in ("mod_id", "mod_name", "mod_version", "mod_license",
              "mod_authors", "mod_description", "loader_version_range",
              "minecraft_version_range", "forge_version_range",
              "minecraft_version", "forge_version"):
        print(f"  {k} = {props.get(k, '<MISSING>')}")

    OUT_RES.mkdir(parents=True, exist_ok=True)

    # 处理所有资源文件
    for src in SRC_RES.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(SRC_RES)
        out = OUT_RES / rel
        out.parent.mkdir(parents=True, exist_ok=True)

        # 只对文本文件做替换
        try:
            text = src.read_text(encoding="utf-8")
            new_text = substitute(text, props)
            out.write_text(new_text, encoding="utf-8")
            if new_text != text:
                print(f"  [substituted] {rel}")
            else:
                print(f"  [copied]      {rel}")
        except UnicodeDecodeError:
            # 二进制文件直接拷贝
            import shutil
            shutil.copy2(src, out)
            print(f"  [binary]      {rel}")

    print(f"\nProcessed resources written to: {OUT_RES}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
