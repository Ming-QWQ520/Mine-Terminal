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
 * ★ 线程安全设计（彻底防 native 崩溃）★
 *
 * TerminalTextBuffer 和 JediTerminal 都是 Kotlin 类，被 jediterm 后台线程
 * 持续修改。渲染线程（MC Render Thread）必须：
 *
 * 1. 在 buf.lock() 内**拷贝**所有需要的数据（行文本、光标位置）
 * 2. 在 buf.unlock() 后用拷贝的数据渲染（不持锁调用 MC API）
 *
 * 这样避免：
 *   - 并发访问 buffer 导致 native 崩溃
 *   - 持锁时调用 MC 渲染 API 导致死锁
 *   - getCursorX()/getCursorY() 线程不安全
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

            // 1. 绘制背景（不需要锁）
            int bg = scheme.getBackgroundRGB();
            String opacity = MineTerminalConfig.CLIENT.backgroundOpacity.get();
            int alpha = 255;
            if ("translucent".equals(opacity)) {
                alpha = MineTerminalConfig.CLIENT.backgroundColorAlpha.get();
            } else if ("transparent".equals(opacity)) {
                alpha = 120;
            }
            graphics.fill(x, y, x + columns * cellWidth, y + rows * cellHeight, withAlpha(bg, alpha));

            // 2. 在锁内拷贝所有行文本（避免持锁渲染）
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
                    // 在锁内读取光标位置（线程安全）
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
                return; // 锁失败则不渲染文本，但背景已画
            }

            // 3. 在锁外绘制文本（用拷贝的数据，线程安全）
            for (int row = 0; row < rows; row++) {
                String text = lineTexts[row];
                if (text == null) continue;
                int lineY = y + row * cellHeight;
                drawTextLine(graphics, font, text, x, lineY, cellWidth, columns);
            }

            // 4. 绘制光标（用拷贝的 cursorX/cursorY，线程安全）
            if (scrollOffset == 0 && session.isAlive() && cursorX >= 0 && cursorY >= 0) {
                drawCursor(graphics, x, y, cellWidth, cellHeight, cursorX, cursorY);
            }
        } catch (Throwable t) {
            LOG.error("[Mine-Terminal] render failed", t);
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
            TerminalTextBuffer buf = session.getBackend().getTextBuffer();
            if (buf == null) return;
            buf.lock();
            try {
                scrollOffset = Math.min(scrollOffset + lines, buf.getHistoryLinesCount());
            } finally {
                buf.unlock();
            }
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
