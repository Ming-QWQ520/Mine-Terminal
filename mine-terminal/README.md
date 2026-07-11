# Mine-Terminal

> **在 Minecraft 内打开完整系统终端** — PTY 驱动 + ANSI/XTerm 全功能终端模拟器

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.20-orange.svg)](https://minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://adoptium.net/)

适用于 **Minecraft Java 1.20.1 + Forge 47.4.20** 的客户端模组。
玩家可以在 Minecraft 窗口里直接运行 vim、tmux、htop、ssh 等全屏终端程序，
体验与原生 OS 终端模拟器毫无差别的交互能力。

---

## ⚠️ 安全声明（务必阅读）

本模组赋予 Minecraft 进程**完整的操作系统 shell 权限**。
通过本终端执行的任何命令，都将以运行 Minecraft 的用户身份执行，拥有该用户的全部权限。

- **切勿** 以 `root` / `Administrator` 身份运行 Minecraft 时启用本模组。
- **切勿** 在多人服务器上加载本模组（本模组为客户端专用，服务端会自禁用）。
- 作者**不**对用户通过本终端执行的任何命令及其后果负责。
- 本模组**不**包含任何网络监听功能，但本地 PTY 进程自身可能访问网络。

如不同意以上条款，请立即删除本模组。

---

## 功能特性

### 1. PTY 伪终端驱动
- 使用 [pty4j](https://github.com/JetBrains/pty4j) 跨平台创建 PTY 进程
- Linux/macOS 原生 fork+exec
- Windows 自动选择 ConPTY（Win10 1809+）或 WinPTY 作为后端
- 支持 TIOCSWINSZ 动态调整窗口尺寸
- 优雅关闭：发送 SIGHUP → destroy → destroyForcibly，杜绝僵尸进程

### 2. 完整 ANSI/XTerm 转义序列解析
- 集成 [jediterm-core](https://github.com/JetBrains/jediterm) 终端模拟内核
- 光标移动、字符属性（颜色/加粗/下划线/闪烁/反色）
- 屏幕清除、滚动区域控制
- 备用屏幕缓冲区（altscreen，vim/tmux/less 必备）
- xterm mouse tracking（可配置 off / always / program）
- 回滚缓冲区（默认 5000 行，可配置）

### 3. 终端 GUI
- 可全屏 / 窗口化的终端界面（Minecraft Screen）
- 半透明背景可配置（transparent / translucent / opaque）
- 内置 3 套配色方案：dark / light / solarized-dark + 自定义
- 光标样式：block / underscore / bar，支持闪烁
- 多标签页：每个标签 = 一个独立 PTY 进程
- 状态栏显示会话名、终端尺寸、运行状态、OS
- 鼠标滚轮滚动回滚缓冲区

### 4. 键盘输入
- 所有玩家按键直接传给 jediterm，由其生成正确转义序列
- 可打印字符、Enter、Backspace、Tab、Escape
- 方向键 / Home / End / PageUp / PageDown / Insert / Delete
- 功能键 F1-F12
- Ctrl+A..Z（控制字符）、Ctrl+[ / \ / ]（ESC / FS / GS）
- Ctrl+Shift+C / V 复制/粘贴（与系统剪贴板交互）
- Alt+key → ESC 前缀

### 5. 多会话
- 终端标签栏，每个标签独立 PTY 进程
- 标签可关闭
- 已退出的会话用红色标记
- Ctrl+Shift+T 新建标签
- Ctrl+Shift+Q 强制关闭界面并终止所有 PTY

### 6. 命令与快捷键
- 默认快捷键：**Y**（游戏中按下打开/关闭终端）
- 聊天命令 `/terminal` 打开
- `/terminal close` 关闭当前界面
- `/terminal killall` 终止所有 PTY 进程

### 7. 配置文件
- `config/mineterm-client.toml` — 客户端配置
- `config/mineterm-common.toml` — 通用配置

---

## 项目结构

```
mine-terminal/
├── build.gradle                            # Gradle 构建脚本 (ForgeGradle + pty4j + jediterm)
├── settings.gradle
├── gradle.properties                       # 版本号、模组元数据
├── gradle/wrapper/gradle-wrapper.properties
├── LICENSE                                 # AGPL-3.0 协议
├── README.md
├── build.sh                                # 构建辅助脚本
└── src/main/
    ├── java/com/mineterm/
    │   ├── MineTerminal.java               # 主模组类
    │   ├── common/
    │   │   └── MineTerminalConfig.java     # ForgeConfigSpec 配置
    │   ├── client/
    │   │   ├── ClientTerminalManager.java  # 客户端管理器（按键检测 + Screen 切换）
    │   │   ├── KeyBindings.java            # 快捷键注册
    │   │   ├── util/
    │   │   │   ├── OSUtil.java             # 平台检测
    │   │   │   └── MCReflect.java          # MC API 反射工具（避免 SRG 混淆）
    │   │   ├── terminal/
    │   │   │   ├── PtyTerminalSession.java       # pty4j 包装
    │   │   │   ├── PtyProcessTtyConnector.java  # jediterm TtyConnector 适配
    │   │   │   ├── JeditermBackend.java         # jediterm-core 集成
    │   │   │   ├── TerminalSession.java         # 高层会话抽象
    │   │   │   ├── TerminalSessionManager.java  # 多会话管理器
    │   │   │   └── TerminalKeyAdapter.java      # MC GLFW 事件 → 终端字节序列
    │   │   └── gui/
    │   │       ├── TerminalColorScheme.java     # 4 套预设配色
    │   │       ├── TerminalRenderer.java        # 字符网格 + 光标 + 滚动渲染
    │   │       └── TerminalScreen.java          # 标签栏 + 终端区 + 状态栏
    │   └── command/
    │       └── TerminalCommand.java        # /terminal 命令
    └── resources/
        ├── META-INF/mods.toml              # 模组元数据
        ├── pack.mcmeta
        └── assets/mineterm/lang/
            ├── en_us.json                  # 英文本地化
            └── zh_cn.json                  # 中文本地化
```

---

## 构建方法

### 环境要求
- JDK 17（必须，Forge 1.20.1 不支持 JDK 21+）
- 互联网连接（首次构建需要下载 Forge MDK、Minecraft 映射、依赖库）

### 标准构建（推荐）

```bash
cd mine-terminal
./gradlew build
```

产物位于 `build/libs/mineterm-0.1.0.jar`，可直接放入 Minecraft 客户端的 `mods/` 目录。

### 在 IDE 中开发

```bash
./gradlew genEclipseRuns    # Eclipse
./gradlew genIntellijRuns   # IntelliJ IDEA
./gradlew runClient         # 直接启动测试客户端
```

---

## 配置说明

### `config/mineterm-client.toml`

```toml
[client.shell]
shellCommand = ""          # 空 = 自动检测（Linux/macOS: $SHELL; Windows: powershell.exe）
shellArgs = ""
initialWorkingDir = ""
termEnv = "xterm-256color"

[client.appearance]
fontSize = 12
lineHeight = 14
cursorStyle = "block"      # block | underscore | bar
cursorBlink = true
colorScheme = "dark"       # dark | light | solarized-dark | custom
customForeground = "#D0D0D0"
customBackground = "#000000"

[client.sizing]
defaultColumns = 80
defaultRows = 24
autoSizeFromWindow = true

[client.behavior]
scrollbackLines = 5000
mouseMode = "program"      # off | always | program
copyOnSelect = false

[client.keys]
openKeyModifier = "ctrl_shift"
openKeyName = "T"
forceCloseKeyModifier = "ctrl_shift"
forceCloseKeyName = "Q"

[client.background]
backgroundOpacity = "translucent"
backgroundColorAlpha = 210

[client.windows]
windowsConptyMode = "auto"

[client.ui]
showTabBar = true
showStatusBar = true
confirmCloseOnProcessExit = true
```

### `config/mineterm-common.toml`

```toml
[common]
enableOnServer = false
logCommandExec = false
```

---

## 跨平台说明

### Linux / macOS
- pty4j 原生支持，无需额外配置。
- 默认 shell 来自 `$SHELL` 环境变量，回退到 `/bin/bash`。

### Windows
- pty4j 自动选择后端：
  - **Windows 10 1809+**：ConPTY（推荐）
  - **旧版本**：WinPTY
- 推荐通过 WSL 使用 Linux 工具链获得最佳终端体验。
- 默认 shell 为 `powershell.exe`。

### 不支持的平台
- 任何 headless（无 GUI）环境，包括专用服务端。
- 移动版 / 主机版 Minecraft。

---

## 快捷键速查

| 按键                       | 作用                              |
| ------------------------- | --------------------------------- |
| `Y`（游戏中）              | 打开 / 关闭终端界面                |
| `/terminal`               | 通过命令打开终端                   |
| `/terminal killall`       | 终止所有 PTY 进程                  |
| `Ctrl+Shift+T`（终端内）   | 新建标签页                         |
| `Ctrl+Shift+Q`（终端内）   | 强制关闭界面并终止所有 PTY         |
| `Ctrl+Shift+C`（终端内）   | 复制选中区域到剪贴板                |
| `Ctrl+Shift+V`（终端内）   | 粘贴剪贴板到终端                   |
| `Esc`（终端内）            | 发送给终端程序（如 vim 退出插入模式）|
| 鼠标滚轮                  | 滚动回滚缓冲区                     |

---

## 技术实现要点

### SRG 混淆问题与反射方案

由于本项目绕过了 ForgeGradle 的 `reobf` 步骤（在受限环境中编译），所有 Minecraft 类的方法和字段在 production 环境会被 SRG 混淆（如 `Minecraft.getInstance()` → `m_91087_()`，`Screen.width` → `f_96543_`）。

为解决此问题，本项目采用以下策略：

1. **所有 MC 类方法/字段访问通过 `MCReflect` 工具类反射**
2. **`@Override` 方法同时提供 SRG 名（`m_xxxxx_`）和 deobf 名两个版本**
3. **SRG 名来自 Forge MCPConfig `joined.tsrg` + Mojang `client.txt` 双重验证**

### 字节码级验证

发布前用 `javap` 全局审计字节码，确保：
- 无任何直接 Minecraft 类方法调用（全部通过反射）
- 所有 `@Override` 方法同时含 SRG 名版本
- 所有反射调用有 try-catch 包裹，失败时降级而非崩溃

---

## 开发路线图

- [x] 阶段 1：搭建 Forge MDK，引入 pty4j + jediterm 依赖
- [x] 阶段 2：jediterm 与 PTY 对接
- [x] 阶段 3：Minecraft Screen + 字符网格渲染
- [x] 阶段 4：键盘事件到终端输入的转换
- [x] 阶段 5：窗口大小变化同步
- [x] 阶段 6：多标签、配置系统、快捷键、UI 美化
- [x] 阶段 7：SRG 混淆适配（反射方案）
- [ ] 阶段 8：完整字体支持（Powerline / Nerd Font）
- [ ] 阶段 9：性能优化（脏矩形重绘）

---

## 开源协议

本项目基于 **[GNU Affero General Public License v3.0](LICENSE)** 开源。

- 你可以自由使用、修改、分发本项目
- 任何基于本项目的衍生作品必须同样以 AGPL-3.0 开源
- 如果你通过网络提供服务（如修改后的 mod 部署在服务器），必须向用户提供源代码
- 详细条款见 [LICENSE](LICENSE) 文件

### 致谢

- [JetBrains / pty4j](https://github.com/JetBrains/pty4j) — 跨平台 PTY 库
- [JetBrains / jediterm](https://github.com/JetBrains/jediterm) — 终端模拟内核
- [Minecraft Forge](https://minecraftforge.net/) — 模组加载器

---

## 反馈与问题

- **Issues**: [https://github.com/Ming-QWQ520/Mine-Terminal/issues](https://github.com/Ming-QWQ520/Mine-Terminal/issues)
- **Author**: [Ming-QWQ520](https://github.com/Ming-QWQ520)

安全相关问题请直接联系作者，**不要**公开披露未修复的漏洞。
