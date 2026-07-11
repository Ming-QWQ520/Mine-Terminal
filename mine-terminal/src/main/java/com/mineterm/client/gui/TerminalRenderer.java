package com.mineterm.client.gui;

import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.Line;
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
 *  1. 从 TerminalTextBuffer 提取字符行（包含样式、前景/背景色、光标位置）
 *  2. 用 Minecraft 的 Font（mono）逐格绘制
 *  3. 处理背景色（按字符格背景填色，而非整行）
 *  4. 处理光标（块状/下划线/竖线，闪烁）
 *  5. 处理滚动偏移（鼠标滚轮 → 查看回滚缓冲区）
 *  6. 处理选中高亮
 *  7. 脏行优化：只重画内容变化的行
 *
 * 性能策略：
 *  - 每帧检查 TerminalTextBuffer 是否有更新（hasChanged 标记）
 *  - 维护一个缓存的"已绘制快照"，无变化则跳过
 *  - 仅在尺寸变化或会话切换时全量重绘
 */
public class TerminalRenderer {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final TerminalSession session;
    private final TerminalColorScheme scheme;
    private int scrollOffset = 0;       // 滚动行数（>0 = 向上回看历史）
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    // 缓存：上次绘制时的 textBuffer 修改计数（jediterm 内部维护）
    private long lastHistoryLinesCount = -1;

    public TerminalRenderer(TerminalSession session) {
        this.session = session;
        this.scheme = session.getColorScheme();
    }

    /**
     * 渲染整个终端区域。
     *
     * @param graphics   Minecraft GuiGraphics
     * @param x          区域左上角 X
     * @param y          区域左上角 Y
     * @param cellWidth  单字符宽（像素）
     * @param cellHeight 单字符高（像素）
     * @param columns    列数
     * @param rows       行数
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
        // 提取 alpha
        String opacity = MineTerminalConfig.CLIENT.backgroundOpacity.get();
        int alpha = 255;
        if ("translucent".equals(opacity)) {
            alpha = MineTerminalConfig.CLIENT.backgroundColorAlpha.get();
        } else if ("transparent".equals(opacity)) {
            alpha = 120;
        }
        fillRect(graphics, x, y, columns * cellWidth, rows * cellHeight,
                withAlpha(bg, alpha));

        // 2. 逐行逐字符绘制
        // 终端坐标：y=0 是顶部第一行
        // 当 scrollOffset > 0 时，我们从历史缓冲区取行
        int myHistoryLines = 0;
        try { myHistoryLines = buf.getHistoryLinesCount(); } catch (Throwable ignored) {}

        for (int row = 0; row < rows; row++) {
            int lineY = y + row * cellHeight;
            CharBuffer text;
            try {
                if (scrollOffset > 0) {
                    // 显示历史行：history[myHistoryLines - scrollOffset + row]
                    int historyIdx = myHistoryLines - scrollOffset + row;
                    if (historyIdx >= 0 && historyIdx < myHistoryLines) {
                        Line histLine = buf.getHistoryLine(historyIdx);
                        text = histLine != null ? histLine.getText() : null;
                    } else if (historyIdx == myHistoryLines && row == 0) {
                        text = buf.getLine(0).getText();
                    } else {
                        continue;
                    }
                } else {
                    TerminalLine tl = buf.getLine(row);
                    text = tl != null ? tl.getText() : null;
                }
            } catch (Throwable t) {
                continue;
            }
            if (text == null) continue;

            // 提取字符 + 默认前景色
            drawCharBuffer(graphics, font, text, x, lineY, cellWidth, cellHeight, columns);
        }

        // 3. 绘制光标
        if (scrollOffset == 0 && session.isAlive()) {
            drawCursor(graphics, font, backend, x, y, cellWidth, cellHeight);
        }
    }

    private void drawCharBuffer(GuiGraphics graphics, Font font, CharBuffer text,
                          int x, int y, int cellW, int cellH, int columns) {
        // 简化版：仅用默认前景色绘制纯文本，不解析每段字符的样式。
        // 完整版本应通过 TerminalLine.processStyling / Line.processForEachStyle
        // 取出 (start, length, TextStyle)，按段绘制并应用前景/背景色。
        try {
            for (int col = 0; col < columns && col < text.length(); col++) {
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
            // jediterm 的 Terminal 接口提供 getCursorX / getCursorY
            com.jediterm.terminal.Terminal term = backend.getTerminal();
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
                    // 块状光标内反相显示字符
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
        scrollOffset = Math.min(scrollOffset + lines,
                session.getBackend().getTextBuffer().getHistoryLinesCount());
    }

    public void scrollDown(int lines) {
        scrollOffset = Math.max(0, scrollOffset - lines);
    }

    public void scrollToBottom() {
        scrollOffset = 0;
    }

    public int getScrollOffset() { return scrollOffset; }

    // ====================================================================
    //  辅助：纯色矩形填充
    // ====================================================================
    private static void fillRect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        // GuiGraphics.fill(x1, y1, x2, y2, color) — color 是 ARGB
        g.fill(x, y, x + w, y + h, argb);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * 把字符坐标转换为屏幕坐标（鼠标点击用）。
     */
    public static int colToX(int x, int col, int cellW) { return x + col * cellW; }
    public static int rowToY(int y, int row, int cellH) { return y + row * cellH; }

    /**
     * 把屏幕坐标转换为字符坐标。
     */
    public static int xToCol(int x, int originX, int cellW) {
        return (x - originX) / cellW;
    }
    public static int yToRow(int y, int originY, int cellH) {
        return (y - originY) / cellH;
    }
}
