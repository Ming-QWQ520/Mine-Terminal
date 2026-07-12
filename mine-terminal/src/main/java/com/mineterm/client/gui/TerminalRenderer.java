package com.mineterm.client.gui;

import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.mineterm.MineTerminal;
import com.mineterm.client.terminal.JeditermBackend;
import com.mineterm.client.terminal.TerminalSession;
import com.mineterm.common.MineTerminalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 终端渲染器：把 jediterm 的字符网格画到 Minecraft GuiGraphics 上。
 *
 * 标准 Forge 开发流程：直接使用 Minecraft.getInstance().font, graphics.fill, graphics.drawString。
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
        Font font = Minecraft.getInstance().font;

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
        graphics.fill(x, y, x + columns * cellWidth, y + rows * cellHeight, withAlpha(bg, alpha));

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
                graphics.drawString(font, String.valueOf(c), px + 1, y + 1, fg, false);
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
                    graphics.fill(px, py + cellH - 2, px + cellW, py + cellH, withAlpha(color, 220));
                    break;
                case "bar":
                    graphics.fill(px, py, px + 2, py + cellH, withAlpha(color, 220));
                    break;
                case "block":
                default:
                    graphics.fill(px, py, px + cellW, py + cellH, withAlpha(color, 180));
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

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
