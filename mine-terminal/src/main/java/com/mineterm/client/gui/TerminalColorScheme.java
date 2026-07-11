package com.mineterm.client.gui;

import com.mineterm.common.MineTerminalConfig;

import java.awt.Color;

/**
 * 终端配色方案。
 *
 * 提供 4 套预设：
 *   - dark           （默认深色）
 *   - light          （浅色）
 *   - solarized-dark
 *   - custom         （从配置文件读取）
 *
 * 每个 scheme 包含：
 *   - 前景色、背景色（RGB int）
 *   - 16 色 ANSI 调色板
 *
 * 配色用 java.awt.Color 表示再转 RGB int，方便 HEX 字符串解析。
 */
public class TerminalColorScheme {

    private final int foreground;
    private final int background;
    private final int[] ansiPalette; // 16 colors

    public TerminalColorScheme(int fg, int bg, int[] ansi16) {
        this.foreground = fg;
        this.background = bg;
        this.ansiPalette = ansi16;
    }

    public int getForegroundRGB() { return foreground; }
    public int getBackgroundRGB() { return background; }
    public int[] getAnsiPalette() { return ansiPalette; }

    public int ansi(int index) {
        if (index < 0 || index >= ansiPalette.length) return foreground;
        return ansiPalette[index];
    }

    // ====================================================================
    //  从配置创建
    // ====================================================================
    public static TerminalColorScheme fromConfig() {
        String name = MineTerminalConfig.CLIENT.colorScheme.get().toLowerCase();
        switch (name) {
            case "light":           return LIGHT;
            case "solarized-dark":  return SOLARIZED_DARK;
            case "custom":
                return new TerminalColorScheme(
                        parseHex(MineTerminalConfig.CLIENT.customForeground.get(), 0xD0D0D0),
                        parseHex(MineTerminalConfig.CLIENT.customBackground.get(), 0x000000),
                        DARK.ansiPalette
                );
            case "dark":
            default:
                return DARK;
        }
    }

    private static int parseHex(String s, int def) {
        if (s == null) return def;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ====================================================================
    //  预设
    // ====================================================================
    public static final TerminalColorScheme DARK = new TerminalColorScheme(
            0xD0D0D0, 0x000000,
            new int[]{
                    0x000000, // 0 black
                    0xCC0000, // 1 red
                    0x4E9A06, // 2 green
                    0xC4A000, // 3 yellow
                    0x3465A4, // 4 blue
                    0x75507B, // 5 magenta
                    0x06989A, // 6 cyan
                    0xD3D7CF, // 7 white
                    0x555753, // 8 bright black
                    0xEF2929, // 9 bright red
                    0x8AE234, // 10 bright green
                    0xFCE94F, // 11 bright yellow
                    0x729FCF, // 12 bright blue
                    0xAD7FA8, // 13 bright magenta
                    0x34E2E2, // 14 bright cyan
                    0xEEEEEC  // 15 bright white
            }
    );

    public static final TerminalColorScheme LIGHT = new TerminalColorScheme(
            0x1A1A1A, 0xF5F5F5,
            new int[]{
                    0x000000, 0xCC0000, 0x4E9A06, 0xC4A000,
                    0x3465A4, 0x75507B, 0x06989A, 0xD3D7CF,
                    0x555753, 0xEF2929, 0x8AE234, 0xFCE94F,
                    0x729FCF, 0xAD7FA8, 0x34E2E2, 0xEEEEEC
            }
    );

    public static final TerminalColorScheme SOLARIZED_DARK = new TerminalColorScheme(
            0x93A1A1, 0x002B36,
            new int[]{
                    0x073642, 0xDC322F, 0x859900, 0xB58900,
                    0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
                    0x002B36, 0xCB4B16, 0x586E75, 0x657B83,
                    0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3
            }
    );
}
