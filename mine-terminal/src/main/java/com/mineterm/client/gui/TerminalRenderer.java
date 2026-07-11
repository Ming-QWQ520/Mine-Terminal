package com.mineterm.client.gui;

import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.mineterm.MineTerminal;
import com.mineterm.client.terminal.JeditermBackend;
import com.mineterm.client.terminal.TerminalSession;
import com.mineterm.client.util.MCReflect;
import com.mineterm.common.MineTerminalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 终端渲染器：把 jediterm 的字符网格画到 Minecraft GuiGraphics 上。
 *
 * 所有 MC 类方法调用都通过 {@link MCReflect} 反射，避免 SRG 混淆。
 */
public class TerminalRenderer {

    private static final org.apache.logging.log4j.Logger LOG = MineTerminal.LOGGER;

    final TerminalSession session;
    private final TerminalColorScheme scheme;
    private int scrollOffset = 0;
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    public TerminalRenderer(TerminalSession session) {
        this.session = session;
        this.scheme = session.getColorScheme();
    }

    public void render(GuiGraphics graphics, int x, int y,
                       int cellWidth, int cellHeight, int columns, int rows) {
        // 用反射获取 Font，避免 Minecraft.getInstance() 被 SRG 混淆
        MCReflect.MinecraftHolder mcHolder = MCReflect.getMinecraft();
        Object fontObj = (mcHolder != null) ? mcHolder.getFont() : null;
        if (fontObj == null || !(fontObj instanceof Font)) return;
        Font font = (Font) fontObj;

        JeditermBackend backend = session.getBackend();
        if (backend == null || backend.getTextBuffer() == null) return;
        TerminalTextBuffer buf = backend.getTextBuffer();

        // 1. 绘制背景
        int bg = scheme.getBackgroundRGB();
        String opacity = MineTerminalConfig.CLIENT.backgroundOpacity.get();
        int alpha = 255;
        if ("translucent".equals(opacity)) {
            alpha = MineTerminalConfig.CLIENT.backgroundColorAlpha.get();
        } else if ("transparent".equals(opacity)) {
            alpha = 120;
        }
        fillRect(graphics, x, y, columns * cellWidth, rows * cellHeight, withAlpha(bg, alpha));

        // 2. 逐行绘制文本
        for (int row = 0; row < rows; row++) {
            int lineY = y + row * cellHeight;
            String text;
            try {
                TerminalLine line = buf.getLine(row);
                if (line == null) continue;
                text = line.getText();
            } catch (Throwable t) {
                continue;
            }
            if (text == null) continue;

            drawTextLine(graphics, font, text, x, lineY, cellWidth, columns);
        }

        // 3. 绘制光标
        if (scrollOffset == 0 && session.isAlive()) {
            drawCursor(graphics, font, backend, x, y, cellWidth, cellHeight);
        }
    }

    private void drawTextLine(GuiGraphics graphics, Font font, String text,
                              int x, int y, int cellW, int columns) {
        try {
            int len = Math.min(text.length(), columns);
            for (int col = 0; col < len; col++) {
                char c = text.charAt(col);
                if (c == 0 || c == ' ') continue;
                int px = x + col * cellW;
                int fg = scheme.getForegroundRGB();
                MCReflect.ggDrawString(graphics, font, String.valueOf(c),
                    px + 1, y + 1, fg, false);
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    private void drawCursor(GuiGraphics graphics, Font font, JeditermBackend backend,
                            int x, int y, int cellW, int cellH) {
        try {
            com.jediterm.terminal.model.JediTerminal term = backend.getTerminal();
            if (term == null) return;
            int cx = term.getCursorX();
            int cy = term.getCursorY();
            if (cx < 0 || cy < 0) return;

            boolean blink = MineTerminalConfig.CLIENT.cursorBlink.get();
            long now = System.currentTimeMillis();
            if (blink) {
                if (now - lastCursorBlink > 500) {
                    cursorVisible = !cursorVisible;
                    lastCursorBlink = now;
                }
            } else {
                cursorVisible = true;
            }
            if (!cursorVisible) return;

            String style = MineTerminalConfig.CLIENT.cursorStyle.get();
            int px = x + cx * cellW;
            int py = y + cy * cellH;
            int color = scheme.getForegroundRGB();

            switch (style) {
                case "underscore":
                    fillRect(graphics, px, py + cellH - 2, cellW, 2, withAlpha(color, 220));
                    break;
                case "bar":
                    fillRect(graphics, px, py, 2, cellH, withAlpha(color, 220));
                    break;
                case "block":
                default:
                    fillRect(graphics, px, py, cellW, cellH, withAlpha(color, 180));
                    break;
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public void scrollUp(int lines) {
        try {
            scrollOffset = Math.min(scrollOffset + lines,
                    session.getBackend().getTextBuffer().getHistoryLinesCount());
        } catch (Throwable t) {}
    }

    public void scrollDown(int lines) {
        scrollOffset = Math.max(0, scrollOffset - lines);
    }

    public void scrollToBottom() { scrollOffset = 0; }
    public int getScrollOffset() { return scrollOffset; }

    private static void fillRect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        MCReflect.ggFill(g, x, y, x + w, y + h, argb);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
