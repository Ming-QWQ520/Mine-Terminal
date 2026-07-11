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
import org.apache.logging.log4j.Logger;

/**
 * 终端渲染器：把 jediterm 的字符网格画到 Minecraft GuiGraphics 上。
 *
 * 实现要点：
 *  1. 从 TerminalTextBuffer 提取每行文本（TerminalLine.getText() → String）
 *  2. 用 Minecraft 的 Font 逐字符绘制（等宽视图）
 *  3. 统一背景色填充（按字符格背景的简化版）
 *  4. 光标渲染（块状/下划线/竖线，闪烁）
 *  5. 滚动偏移（鼠标滚轮 → 查看回滚缓冲区）
 *
 * 性能策略：
 *  - 仅当终端内容变化时重绘
 *  - 简化版：每帧重绘，但 MC 字符绘制效率高，可接受
 */
public class TerminalRenderer {

    private static final Logger LOG = MineTerminal.LOGGER;

    final TerminalSession session;
    private final TerminalColorScheme scheme;
    private int scrollOffset = 0;
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    public TerminalRenderer(TerminalSession session) {
        this.session = session;
        this.scheme = session.getColorScheme();
    }

    /**
     * 渲染整个终端区域。
     */
    public void render(GuiGraphics graphics, int x, int y,
                       int cellWidth, int cellHeight, int columns, int rows) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

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
        fillRect(graphics, x, y, columns * cellWidth, rows * cellHeight,
                withAlpha(bg, alpha));

        // 2. 逐行绘制文本
        // TerminalLine.getText() 返回 String，可直接遍历
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

            drawTextLine(graphics, font, text, x, lineY, cellWidth, cellHeight, columns);
        }

        // 3. 绘制光标
        if (scrollOffset == 0 && session.isAlive()) {
            drawCursor(graphics, font, backend, x, y, cellWidth, cellHeight);
        }
    }

    private void drawTextLine(GuiGraphics graphics, Font font, String text,
                              int x, int y, int cellW, int cellH, int columns) {
        // 简化版：仅用默认前景色绘制纯文本，不解析每段字符的样式。
        // 完整版本应通过 TerminalLine.process(styledTextConsumer) 取出 (start, length, TextStyle)
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

            // 闪烁
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

    // ====================================================================
    //  滚动
    // ====================================================================
    public void scrollUp(int lines) {
        try {
            scrollOffset = Math.min(scrollOffset + lines,
                    session.getBackend().getTextBuffer().getHistoryLinesCount());
        } catch (Throwable t) {
            // ignore
        }
    }

    public void scrollDown(int lines) {
        scrollOffset = Math.max(0, scrollOffset - lines);
    }

    public void scrollToBottom() {
        scrollOffset = 0;
    }

    public int getScrollOffset() { return scrollOffset; }

    // ====================================================================
    //  辅助
    // ====================================================================
    private static void fillRect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int colToX(int x, int col, int cellW) { return x + col * cellW; }
    public static int rowToY(int y, int row, int cellH) { return y + row * cellH; }
    public static int xToCol(int x, int originX, int cellW) {
        return (x - originX) / cellW;
    }
    public static int yToRow(int y, int originY, int cellH) {
        return (y - originY) / cellH;
    }
}
