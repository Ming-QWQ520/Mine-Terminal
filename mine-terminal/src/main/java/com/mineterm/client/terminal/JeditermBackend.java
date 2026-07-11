package com.mineterm.client.terminal;

import com.jediterm.core.Color;
import com.jediterm.core.TerminalColor;
import com.jediterm.core.TextStyle;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.StyledTextConsumerAdapter;
import com.jediterm.terminal.TextStyleChangeEvent;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalColorScheme;
import com.mineterm.common.MineTerminalConfig;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * jediterm 终端后端封装。
 *
 * 集成 jediterm-core，提供：
 *   - Terminal 接口（核心终端状态机）
 *   - TerminalTextBuffer（字符网格 + 样式 + 回滚缓冲区）
 *   - 与 PtyProcessTtyConnector 对接的 TtyBasedArrayDataStream
 *   - 自定义 SettingsProvider（配色方案、字体、光标样式来自配置文件）
 *   - 终端尺寸动态调整
 *
 * 注意：jediterm 的 UI 层 (JediTerminalWidget) 基于 Swing，
 * 在 Minecraft 中不能用 Swing。我们只用 jediterm-core 的内核：
 *   - Terminal                  → 状态机
 *   - TerminalTextBuffer        → 字符网格
 *   - TerminalOutput            → 消费解析后的转义序列
 *
 * 渲染由我们自己实现 TerminalRenderer 完成。
 */
public class JeditermBackend {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final PtyTerminalSession session;
    private final PtyProcessTtyConnector connector;
    private final TerminalColorScheme colorScheme;

    private SettingsProvider settingsProvider;
    private TerminalStarter terminalStarter;
    private TerminalTextBuffer textBuffer;
    private Terminal terminal;
    private TerminalDisplay display;   // 我们的渲染器实现这个接口

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean closed = false;

    public JeditermBackend(PtyTerminalSession session, TerminalColorScheme colorScheme) {
        this.session = session;
        this.colorScheme = colorScheme;
        this.connector = new PtyProcessTtyConnector(session);
    }

    public void setDisplay(TerminalDisplay display) {
        this.display = display;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                settingsProvider = new MineTerminalSettingsProvider(colorScheme);
                textBuffer = new TerminalTextBuffer(
                        session.getColumns(),
                        session.getRows(),
                        new StyleState(),
                        settingsProvider.getBufferMaxLinesCount(),
                        false  // separable buffer
                );
                // Terminal 是 jediterm-core 的核心状态机
                terminal = new TerminalImpl(
                        session.getColumns(),
                        session.getRows(),
                        settingsProvider,
                        textBuffer,
                        display != null ? display : new NoopTerminalDisplay()
                );

                terminalStarter = new TerminalStarter(
                        terminal,
                        connector,
                        new TtyBasedArrayDataStream(connector),
                        settingsProvider,
                        Collections.<HyperlinkFilter>emptyList()
                );

                terminal.setModeEnabled(TerminalMode.AutoNewLine, false);
                terminal.setModeEnabled(TerminalMode.CursorKey, true);

                // 启动后台线程从 PTY 读数据
                Thread reader = new Thread(terminalStarter.getTerminalReader(),
                        "mine-terminal-jediterm-reader-" + session.getSessionName());
                reader.setDaemon(true);
                reader.start();

                Thread writer = new Thread(terminalStarter.getTerminalWriter(),
                        "mine-terminal-jediterm-writer-" + session.getSessionName());
                writer.setDaemon(true);
                writer.start();

                LOG.info("[Mine-Terminal] jediterm backend started: session={}", session.getSessionName());
            } catch (Throwable t) {
                LOG.error("[Mine-Terminal] Failed to start jediterm backend", t);
                throw new RuntimeException(t);
            }
        }
    }

    public TerminalTextBuffer getTextBuffer() { return textBuffer; }
    public Terminal getTerminal() { return terminal; }
    public TerminalColorScheme getColorScheme() { return colorScheme; }
    public SettingsProvider getSettingsProvider() { return settingsProvider; }

    /**
     * 处理玩家键盘输入：通过 Terminal.processByte / processCodePoint 写入 jediterm。
     * jediterm 会根据当前模式（cursor key / application key）自动转换为正确的转义序列，
     * 然后通过 TtyConnector.write 发送到 PTY。
     */
    public void processInputBytes(byte[] data) {
        if (terminal == null || closed) return;
        try {
            // 直接写入 jediterm 的输入端
            terminalStarter.getTerminalWriter().writeBytesToTerminal(data);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] Failed to process input: {}", t.getMessage());
        }
    }

    public void resize(int cols, int rows) {
        if (terminal == null) return;
        try {
            terminal.resize(new com.jediterm.core.TerminalSize(cols, rows));
            session.resize(cols, rows);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] resize failed: {}", t.getMessage());
        }
    }

    public void close() {
        closed = true;
        try {
            if (terminalStarter != null) {
                terminalStarter.close();
            }
        } catch (Throwable t) {
            LOG.debug("[Mine-Terminal] terminalStarter.close error: {}", t.getMessage());
        }
        session.destroy();
    }

    /**
     * 自定义 SettingsProvider，使用配置文件中的配色与字体。
     */
    public static class MineTerminalSettingsProvider extends DefaultSettingsProvider {
        private final TerminalColorScheme scheme;

        public MineTerminalSettingsProvider(TerminalColorScheme scheme) {
            super();
            this.scheme = scheme;
        }

        @Override
        public TextStyle getDefaultStyle() {
            return new TextStyle(
                    TerminalColor.rgb(new Color(scheme.getForegroundRGB())),
                    TerminalColor.rgb(new Color(scheme.getBackgroundRGB()))
            );
        }

        @Override
        public TextStyle getSelectionColor() {
            return new TextStyle(
                    TerminalColor.rgb(new Color(0x404040)),
                    TerminalColor.rgb(new Color(0x606060))
            );
        }

        @Override
        public TextStyle getFoundPatternColor() {
            return new TextStyle(
                    TerminalColor.rgb(new Color(0xFFFF00)),
                    TerminalColor.rgb(new Color(0x404000))
            );
        }

        @Override
        public int getBufferMaxLinesCount() {
            return MineTerminalConfig.CLIENT.scrollbackLines.get();
        }

        @Override
        public boolean useInverseSelectionColor() { return true; }

        @Override
        public boolean emulateX11CopyPaste() { return false; }

        @Override
        public boolean copyOnSelect() {
            return MineTerminalConfig.CLIENT.copyOnSelect.get();
        }

        @Override
        public boolean pasteOnMiddleMouseClick() { return true; }

        @Override
        public boolean mouseWheelReporting() {
            String mode = MineTerminalConfig.CLIENT.mouseMode.get();
            return !"off".equals(mode);
        }
    }

    /**
     * 空实现的 TerminalDisplay，当我们还没有真实渲染器时使用。
     */
    private static class NoopTerminalDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) {}
        @Override public void setCursorShape(com.jediterm.terminal.ui.CursorShape shape) {}
        @Override public void setBlinkingCursor(boolean enabled) {}
        @Override public void beep() {}
        @Override public void scrollArea(int scrollRegionTop, int scrollRegionBottom, int n) {}
        @Override public void setScrollingEnabled(boolean enabled) {}
        @Override public void setTitle(String title) {}
        @Override public void setCurrentPath(String path) {}
        @Override public void terminalMouseMoved(int x, int y, int modifiers) {}
        @Override public void requestResize(com.jediterm.core.TerminalSize newSize, boolean origin, int cursorY) {}
        @Override public void setCursorVisible(boolean visible) {}
        @Override public boolean isCursorVisible() { return true; }
        @Override public void setSelection(com.jediterm.terminal.model.TerminalSelection selection) {}
        @Override public void repaint() {}
        @Override public void consume(StyledTextConsumer consumer) {}
        @Override public void consumeLine(int x, int y, StyledTextConsumer consumer) {}
        @Override public void pauseResizing() {}
        @Override public void resumeResizing() {}
        @Override public void setAntialiasing(boolean value) {}
        @Override public void setMouseCursorShape(int shape) {}
        @Override public void onError(Throwable e) {}
        @Override public void onTextChange() {}
        @Override public void onTerminalAreaChanged(int width, int height) {}
        @Override public void onScrollingAreaChanged(int scrollRegionTop, int scrollRegionBottom) {}
        @Override public void requestTermType() {}
        @Override public void requestSize() {}
        @Override public void onTitleChanged(String title) {}
    }
}
