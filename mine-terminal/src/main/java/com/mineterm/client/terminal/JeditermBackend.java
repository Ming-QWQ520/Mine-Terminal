package com.mineterm.client.terminal;

import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalExecutorServiceManager;
import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalColorScheme;
import com.mineterm.common.MineTerminalConfig;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * jediterm 终端后端封装。
 *
 * 集成 jediterm-core 3.73 的真实 API：
 *   - JediTerminal(TerminalDisplay, TerminalTextBuffer, StyleState)  核心终端状态机
 *   - TerminalTextBuffer(int, int, StyleState, int)                  字符网格 + 回滚
 *   - TerminalStarter(JediTerminal, TtyConnector, TerminalDataStream, TerminalTypeAheadManager, TerminalExecutorServiceManager)
 *   - TtyBasedArrayDataStream(TtyConnector)                          PTY 输出 → ANSI 解析
 *
 * 不依赖 jediterm-ui（SettingsProvider 在 jediterm-ui 中，但本模组用 MC 自己的渲染，
 * 所以不需要 SettingsProvider）。配色方案直接通过 TerminalColorScheme 使用。
 *
 * 渲染由 TerminalRenderer 完成（实现 TerminalDisplay 接口的部分功能）。
 */
public class JeditermBackend {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final PtyTerminalSession session;
    private final PtyProcessTtyConnector connector;
    private final TerminalColorScheme colorScheme;

    private TerminalStarter terminalStarter;
    private TerminalTextBuffer textBuffer;
    private JediTerminal terminal;
    private TerminalDisplay display;
    private StyleState styleState;

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
                // 创建 StyleState 并设置默认前景/背景色（来自配色方案）
                styleState = new StyleState();
                int fg = colorScheme.getForegroundRGB();
                int bg = colorScheme.getBackgroundRGB();
                styleState.setDefaultStyle(new TextStyle(
                        new TerminalColor((fg >> 16) & 0xFF, (fg >> 8) & 0xFF, fg & 0xFF),
                        new TerminalColor((bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF)
                ));

                // 创建字符网格 + 回滚缓冲区
                // TerminalTextBuffer(int width, int height, StyleState, int maxLinesCount)
                int scrollback = MineTerminalConfig.CLIENT.scrollbackLines.get();
                textBuffer = new TerminalTextBuffer(
                        session.getColumns(),
                        session.getRows(),
                        styleState,
                        scrollback
                );

                // 创建 JediTerminal — jediterm-core 的核心状态机
                TerminalDisplay disp = (display != null) ? display : new NoopTerminalDisplay();
                terminal = new JediTerminal(disp, textBuffer, styleState);

                // TerminalStarter 串起 JediTerminal + TtyConnector + DataStream
                // TypeAheadManager 传 null 会导致 NPE，创建一个禁用 typeahead 的 manager
                TerminalTypeAheadManager typeAhead = new TerminalTypeAheadManager(new NoopTypeAheadModel());
                TerminalExecutorServiceManager execMgr = new SimpleTerminalExecutorServiceManager();

                terminalStarter = new TerminalStarter(
                        terminal,
                        connector,
                        new TtyBasedArrayDataStream(connector),
                        typeAhead,
                        execMgr
                );

                // 配置终端模式
                terminal.setModeEnabled(TerminalMode.AutoNewLine, false);
                terminal.setModeEnabled(TerminalMode.CursorKey, true);

                // 启动后台读取线程（jediterm 内部会 fork 一个线程持续读取 PTY）
                terminalStarter.start();

                LOG.info("[Mine-Terminal] jediterm backend started: session={}", session.getSessionName());
            } catch (Throwable t) {
                LOG.error("[Mine-Terminal] Failed to start jediterm backend", t);
                throw new RuntimeException(t);
            }
        }
    }

    public TerminalTextBuffer getTextBuffer() { return textBuffer; }
    public JediTerminal getTerminal() { return terminal; }
    public TerminalColorScheme getColorScheme() { return colorScheme; }
    public StyleState getStyleState() { return styleState; }
    public TerminalStarter getTerminalStarter() { return terminalStarter; }

    /**
     * 处理玩家键盘输入：通过 TerminalStarter 发送到 PTY。
     * jediterm 会根据当前模式（cursor key / application key）自动转换为正确的转义序列。
     */
    public void processInputBytes(byte[] data) {
        if (terminalStarter == null || closed) return;
        try {
            terminalStarter.sendBytes(data, false);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] Failed to process input: {}", t.getMessage());
        }
    }

    /**
     * 调整终端尺寸：通知 JediTerminal + PTY。
     */
    public void resize(int cols, int rows) {
        if (terminal == null) return;
        try {
            TermSize newSize = new TermSize(cols, rows);
            terminalStarter.postResize(newSize, RequestOrigin.User);
            session.resize(cols, rows);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] resize failed: {}", t.getMessage());
        }
    }

    public void close() {
        closed = true;
        try {
            if (terminalStarter != null) {
                terminalStarter.requestEmulatorStop();
                terminalStarter.close();
            }
        } catch (Throwable t) {
            LOG.debug("[Mine-Terminal] terminalStarter.close error: {}", t.getMessage());
        }
        session.destroy();
    }

    /**
     * 空实现的 TerminalDisplay，当我们还没有真实渲染器时使用。
     * jediterm-core 3.73 的 TerminalDisplay 接口。
     */
    private static class NoopTerminalDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) {}
        @Override public void setCursorShape(CursorShape shape) {}
        @Override public void beep() {}
        @Override public void scrollArea(int scrollRegionTop, int scrollRegionBottom, int n) {}
        @Override public void setCursorVisible(boolean visible) {}
        @Override public void useAlternateScreenBuffer(boolean enabled) {}
        @Override public String getWindowTitle() { return ""; }
        @Override public void setWindowTitle(String title) {}
        @Override public com.jediterm.terminal.model.TerminalSelection getSelection() { return null; }
        @Override public void terminalMouseModeSet(com.jediterm.terminal.emulator.mouse.MouseMode mode) {}
        @Override public void setMouseFormat(com.jediterm.terminal.emulator.mouse.MouseFormat format) {}
        @Override public boolean ambiguousCharsAreDoubleWidth() { return false; }
    }

    /**
     * TerminalExecutorServiceManager 接口的简单实现。
     * jediterm 3.73 中该类型是接口，需要自行实现。
     */
    private static class SimpleTerminalExecutorServiceManager implements TerminalExecutorServiceManager {
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mine-terminal-jediterm-scheduler");
            t.setDaemon(true);
            return t;
        });
        private final ExecutorService unbounded = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mine-terminal-jediterm-worker");
            t.setDaemon(true);
            return t;
        });

        @Override
        public ScheduledExecutorService getSingleThreadScheduledExecutor() { return scheduler; }

        @Override
        public ExecutorService getUnboundedExecutorService() { return unbounded; }

        @Override
        public void shutdownWhenAllExecuted() {
            scheduler.shutdown();
            unbounded.shutdown();
        }
    }

    /**
     * 空实现的 TypeAheadTerminalModel，禁用 typeahead 预测功能。
     * 避免传 null 给 TerminalTypeAheadManager 导致 NPE。
     */
    private static class NoopTypeAheadModel implements com.jediterm.core.typeahead.TypeAheadTerminalModel {
        @Override public void insertCharacter(char c, int x) {}
        @Override public void removeCharacters(int x, int count) {}
        @Override public void moveCursor(int x) {}
        @Override public void forceRedraw() {}
        @Override public void clearPredictions() {}
        @Override public void lock() {}
        @Override public void unlock() {}
        @Override public boolean isUsingAlternateBuffer() { return false; }
        @Override public Object getCurrentLineWithCursor() { return null; }
        @Override public int getTerminalWidth() { return 80; }
        @Override public boolean isTypeAheadEnabled() { return false; }
        @Override public long getLatencyThreshold() { return Long.MAX_VALUE; }
        @Override public Object getShellType() { return null; }
    }
}
