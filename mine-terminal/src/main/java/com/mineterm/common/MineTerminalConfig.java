package com.mineterm.common;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mine-Terminal 配置。
 *
 * 客户端配置文件: config/mine-terminal-client.toml
 * 通用配置文件:   config/mine-terminal-common.toml
 *
 * 涵盖：
 *  - Shell 路径与参数
 *  - 默认终端尺寸
 *  - 字体大小 / 行间距
 *  - 配色方案
 *  - 快捷键绑定
 *  - 回滚行数限制
 *  - 鼠标事件模式
 *  - Windows 特殊配置
 */
public class MineTerminalConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        // -------- 客户端配置 --------
        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        CLIENT = new Client(cb);
        CLIENT_SPEC = cb.build();

        // -------- 通用配置 --------
        ForgeConfigSpec.Builder ob = new ForgeConfigSpec.Builder();
        COMMON = new Common(ob);
        COMMON_SPEC = ob.build();
    }

    // =====================================================================
    // 客户端配置
    // =====================================================================
    public static class Client {
        public final ForgeConfigSpec.ConfigValue<String> shellCommand;
        public final ForgeConfigSpec.ConfigValue<String> shellArgs;
        public final ForgeConfigSpec.ConfigValue<String> initialWorkingDir;
        public final ForgeConfigSpec.ConfigValue<String> termEnv;

        public final ForgeConfigSpec.IntValue fontSize;
        public final ForgeConfigSpec.IntValue lineHeight;
        public final ForgeConfigSpec.ConfigValue<String> cursorStyle; // block | underscore | bar
        public final ForgeConfigSpec.BooleanValue cursorBlink;

        public final ForgeConfigSpec.ConfigValue<String> colorScheme; // dark | light | solarized-dark | custom
        public final ForgeConfigSpec.ConfigValue<String> customForeground;
        public final ForgeConfigSpec.ConfigValue<String> customBackground;

        public final ForgeConfigSpec.IntValue defaultColumns;
        public final ForgeConfigSpec.IntValue defaultRows;
        public final ForgeConfigSpec.BooleanValue autoSizeFromWindow;

        public final ForgeConfigSpec.IntValue scrollbackLines;
        public final ForgeConfigSpec.ConfigValue<String> mouseMode; // off | always | program
        public final ForgeConfigSpec.BooleanValue copyOnSelect;

        public final ForgeConfigSpec.ConfigValue<String> openKeyModifier; // ctrl_shift | alt | none
        public final ForgeConfigSpec.ConfigValue<String> openKeyName;
        public final ForgeConfigSpec.ConfigValue<String> forceCloseKeyModifier;
        public final ForgeConfigSpec.ConfigValue<String> forceCloseKeyName;

        public final ForgeConfigSpec.ConfigValue<String> backgroundOpacity; // transparent | translucent | opaque
        public final ForgeConfigSpec.IntValue backgroundColorAlpha;

        public final ForgeConfigSpec.ConfigValue<String> windowsConptyMode; // auto | conpty | winpty | wsl

        public final ForgeConfigSpec.BooleanValue showTabBar;
        public final ForgeConfigSpec.BooleanValue showStatusBar;
        public final ForgeConfigSpec.BooleanValue confirmCloseOnProcessExit;

        public Client(ForgeConfigSpec.Builder b) {
            b.comment("Mine-Terminal — client settings").push("client");

            b.comment("Shell configuration").push("shell");
            shellCommand = b.comment("Path or name of the shell executable. Empty = autodetect.")
                    .define("shellCommand", "");
            shellArgs = b.comment("Space-separated extra args passed to the shell.")
                    .define("shellArgs", "");
            initialWorkingDir = b.comment("Initial working directory. Empty = user home.")
                    .define("initialWorkingDir", "");
            termEnv = b.comment("Value of the TERM environment variable.")
                    .define("termEnv", "xterm-256color");
            b.pop();

            b.comment("Appearance").push("appearance");
            fontSize = b.comment("Font size in pixels (height).")
                    .defineInRange("fontSize", 12, 6, 48);
            lineHeight = b.comment("Line height in pixels (cell height).")
                    .defineInRange("lineHeight", 14, 8, 64);
            cursorStyle = b.comment("Cursor style: block | underscore | bar")
                    .define("cursorStyle", "block");
            cursorBlink = b.comment("Whether the cursor should blink.")
                    .define("cursorBlink", true);
            colorScheme = b.comment("Color scheme preset: dark | light | solarized-dark | custom")
                    .define("colorScheme", "dark");
            customForeground = b.comment("Custom foreground hex color (used only if colorScheme=custom).")
                    .define("customForeground", "#D0D0D0");
            customBackground = b.comment("Custom background hex color (used only if colorScheme=custom).")
                    .define("customBackground", "#000000");
            b.pop();

            b.comment("Window sizing").push("sizing");
            defaultColumns = b.defineInRange("defaultColumns", 80, 10, 500);
            defaultRows = b.defineInRange("defaultRows", 24, 4, 200);
            autoSizeFromWindow = b.comment("If true, terminal grid auto-fits the GUI window.")
                    .define("autoSizeFromWindow", true);
            b.pop();

            b.comment("Scrollback & mouse").push("behavior");
            scrollbackLines = b.defineInRange("scrollbackLines", 5000, 100, 100000);
            mouseMode = b.comment("Mouse reporting mode: off | always | program")
                    .define("mouseMode", "program");
            copyOnSelect = b.comment("If true, selecting text automatically copies to clipboard.")
                    .define("copyOnSelect", false);
            b.pop();

            b.comment("Keybindings").push("keys");
            openKeyModifier = b.define("openKeyModifier", "ctrl_shift");
            openKeyName = b.define("openKeyName", "T");
            forceCloseKeyModifier = b.define("forceCloseKeyModifier", "ctrl_shift");
            forceCloseKeyName = b.define("forceCloseKeyName", "Q");
            b.pop();

            b.comment("Background").push("background");
            backgroundOpacity = b.comment("Background opacity: transparent | translucent | opaque")
                    .define("backgroundOpacity", "translucent");
            backgroundColorAlpha = b.comment("Background alpha 0-255 (only used when opacity=translucent).")
                    .defineInRange("backgroundColorAlpha", 210, 0, 255);
            b.pop();

            b.comment("Windows-specific").push("windows");
            windowsConptyMode = b.comment("Windows backend: auto | conpty | winpty | wsl")
                    .define("windowsConptyMode", "auto");
            b.pop();

            b.comment("UI panels").push("ui");
            showTabBar = b.define("showTabBar", true);
            showStatusBar = b.define("showStatusBar", true);
            confirmCloseOnProcessExit = b.define("confirmCloseOnProcessExit", true);
            b.pop();

            b.pop();
        }
    }

    // =====================================================================
    // 通用配置（客户端+服务端共享，但本模组主要在客户端使用）
    // =====================================================================
    public static class Common {
        public final ForgeConfigSpec.BooleanValue enableOnServer;
        public final ForgeConfigSpec.BooleanValue logCommandExec;

        public Common(ForgeConfigSpec.Builder b) {
            b.comment("Mine-Terminal — common settings").push("common");
            enableOnServer = b.comment("If false, the mod refuses to load on dedicated servers.")
                    .define("enableOnServer", false);
            logCommandExec = b.comment("If true, every shell command execution is logged (for audit).")
                    .define("logCommandExec", false);
            b.pop();
        }
    }
}
