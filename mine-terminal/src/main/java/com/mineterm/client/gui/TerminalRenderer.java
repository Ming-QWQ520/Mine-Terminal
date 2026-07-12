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
 * ★ 核心渲染策略 ★
 *
 * Minecraft 默认字体不是等宽字体。不同字符宽度不同（'i' 比 'm' 窄）。
 * 终端模拟器需要等宽显示，所以我们：
 *
 * 1. 计算等宽单元宽度 cellW = font.width('M')（用最宽字符作为基准）
 * 2. 每个字符在其单元格内居中绘制：(cellW - font.width(c)) / 2
 * 3. 背景填充按等宽 cellW 定位
 * 4. 光标按等宽 cellW 定位
 *
 * 这样无论字符实际宽度如何，显示位置都是等距的。
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
        try {
            Font font = Minecraft.getInstance().font;
            JeditermBackend backend = session.getBackend();
            if (backend == null || backend.getTextBuffer() == null) return;
            TerminalTextBuffer buf = backend.getTextBuffer();

            // 计算实际等宽单元宽度 — 用 'M' 作为基准（MC 字体中较宽的字符）
            int actualCellW = font.width("M");
            if (actualCellW < 1) actualCellW = cellWidth;
            // 确保 cellW 至少为 actualCellW
            int renderCellW = Math.max(cellWidth, actualCellW);

            // 1. 绘制背景
            int bg = scheme.getBackgroundRGB();
            String opacity = MineTerminalConfig.CLIENT.backgroundOpacity.get();
            int alpha = 255;
            if ("translucent".equals(opacity)) {
                alpha = MineTerminalConfig.CLIENT.backgroundColorAlpha.get();
            } else if ("transparent".equals(opacity)) {
                alpha = 120;
            }
            graphics.fill(x, y, x + columns * renderCellW, y + rows * cellHeight, withAlpha(bg, alpha));

            // 2. 在锁内拷贝所有行文本
            String[] lineTexts = new String[rows];
            int cursorX = -1, cursorY = -1;
            try {
                buf.lock();
                try {
                    for (int row = 0; row < rows; row++) {
                        try {
                            TerminalLine line = buf.getLine(row);
                            lineTexts[row] = (line != null) ? line.getText() : null;
                        } catch (Throwable t) {
                            lineTexts[row] = null;
                        }
                    }
                    try {
                        com.jediterm.terminal.model.JediTerminal term = backend.getTerminal();
                        if (term != null) {
                            cursorX = term.getCursorX();
                            cursorY = term.getCursorY();
                        }
                    } catch (Throwable t) {
                        // 光标读取失败不影响主渲染
                    }
                } finally {
                    buf.unlock();
                }
            } catch (Throwable t) {
                LOG.warn("[Mine-Terminal] Failed to lock text buffer: {}", t.getMessage());
                return;
            }

            // 3. 在锁外绘制文本
            for (int row = 0; row < rows; row++) {
                String text = lineTexts[row];
                if (text == null) continue;
                int lineY = y + row * cellHeight;
                drawTextLine(graphics, font, text, x, lineY, renderCellW, columns);
            }

            // 4. 绘制光标
            if (scrollOffset == 0 && session.isAlive() && cursorX >= 0 && cursorY >= 0) {
                drawCursor(graphics, x, y, renderCellW, cellHeight, cursorX, cursorY);
            }
        } catch (Throwable t) {
            LOG.error("[Mine-Terminal] render failed", t);
        }
    }

    /**
     * 绘制一行文本。
     * 每个字符在其等宽单元格内居中显示。
     */
    private void drawTextLine(GuiGraphics graphics, Font font, String text,
                              int x, int y, int cellW, int columns) {
        try {
            int len = Math.min(text.length(), columns);
            for (int col = 0; col < len; col++) {
                char c = text.charAt(col);
                if (c == 0 || c == ' ') continue;
                String cs = String.valueOf(c);
                int charW = font.width(cs);
                // 字符在单元格内居中
                int px = x + col * cellW + (cellW - charW) / 2;
                int fg = scheme.getForegroundRGB();
                graphics.drawString(font, cs, px, y, fg, false);
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    private void drawCursor(GuiGraphics graphics, int x, int y, int cellW, int cellH,
                            int cursorX, int cursorY) {
        try {
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
            int px = x + cursorX * cellW;
            int py = y + cursorY * cellH;
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
