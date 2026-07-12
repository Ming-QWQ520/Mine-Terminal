package com.mineterm.client.terminal;

import com.jediterm.core.util.TermSize;
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
 * 完全不使用 jediterm 的 TerminalStarter/TerminalTypeAheadManager
 * （它们会启动后台线程导致 native 崩溃）。
 *
 * 改为：自己管理进程读取线程，手动将输出送入 JediTerminal 解析。
 */
public class JeditermBackend {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final PtyTerminalSession session;
    private final PtyProcessTtyConnector connector;
    private final TerminalColorScheme colorScheme;

    private TerminalTextBuffer textBuffer;
    private JediTerminal terminal;
    private StyleState styleState;
    private Thread readerThread;
    private volatile boolean closed = false;

    private final AtomicBoolean started = new AtomicBoolean(false);

    public JeditermBackend(PtyTerminalSession session, TerminalColorScheme colorScheme) {
        this.session = session;
        this.colorScheme = colorScheme;
        this.connector = new PtyProcessTtyConnector(session);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                // 创建 StyleState
                styleState = new StyleState();
                int fg = colorScheme.getForegroundRGB();
                int bg = colorScheme.getBackgroundRGB();
                styleState.setDefaultStyle(new TextStyle(
                        new TerminalColor((fg >> 16) & 0xFF, (fg >> 8) & 0xFF, fg & 0xFF),
                        new TerminalColor((bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF)
                ));

                // 创建字符网格
                int scrollback = MineTerminalConfig.CLIENT.scrollbackLines.get();
                textBuffer = new TerminalTextBuffer(
                        session.getColumns(),
                        session.getRows(),
                        styleState,
                        scrollback
                );

                // 创建 JediTerminal
                TerminalDisplay disp = new NoopTerminalDisplay();
                terminal = new JediTerminal(disp, textBuffer, styleState);

                // 配置终端模式
                terminal.setModeEnabled(TerminalMode.AutoNewLine, false);
                terminal.setModeEnabled(TerminalMode.CursorKey, true);

                // ★ 不使用 TerminalStarter — 它会启动后台线程导致崩溃
                // ★ 改为自己管理读取线程
                readerThread = new Thread(this::readLoop,
                        "TerminalReader-" + session.getSessionName());
                readerThread.setDaemon(true);
                readerThread.start();

                LOG.info("[Mine-Terminal] Terminal backend started: session={}", session.getSessionName());
            } catch (Throwable t) {
                LOG.error("[Mine-Terminal] Failed to start backend", t);
            }
        }
    }

    /**
     * 读取进程输出并送入 JediTerminal 解析。
     * 自己管理线程，不依赖 TerminalStarter。
     */
    private void readLoop() {
        byte[] buf = new byte[4096];
        while (!closed && session.isAlive()) {
            try {
                int n = readFromProcess(buf);
                if (n <= 0) {
                    Thread.sleep(50);
                    continue;
                }
                // 将字节送入 JediEmulator 解析
                // 用 ArrayTerminalDataStream 包装数据
                char[] chars = new char[n];
                for (int i = 0; i < n; i++) {
                    chars[i] = (char) (buf[i] & 0xFF);
                }
                com.jediterm.terminal.ArrayTerminalDataStream dataStream =
                    new com.jediterm.terminal.ArrayTerminalDataStream(chars, 0, n);
                com.jediterm.terminal.emulator.JediEmulator emulator =
                    new com.jediterm.terminal.emulator.JediEmulator(dataStream, terminal);

                textBuffer.lock();
                try {
                    while (emulator.hasNext()) {
                        emulator.next();
                    }
                } finally {
                    textBuffer.unlock();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // 静默，继续读取
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        LOG.info("[Mine-Terminal] Reader thread exited: session={}", session.getSessionName());
    }

    private int readFromProcess(byte[] buf) {
        try {
            java.io.InputStream is = session.getOutputStreamFromPty();
            if (is == null) return -1;
            int available = is.available();
            if (available <= 0) return 0;
            int toRead = Math.min(available, buf.length);
            return is.read(buf, 0, toRead);
        } catch (Exception e) {
            return -1;
        }
    }

    public TerminalTextBuffer getTextBuffer() { return textBuffer; }
    public JediTerminal getTerminal() { return terminal; }
    public TerminalColorScheme getColorScheme() { return colorScheme; }
    public StyleState getStyleState() { return styleState; }

    /**
     * 处理玩家键盘输入：直接写入进程 stdin。
     */
    public void processInputBytes(byte[] data) {
        if (closed) return;
        try {
            session.writeToPty(data, 0, data.length);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] Failed to write input: {}", t.getMessage());
        }
    }

    /**
     * 调整终端尺寸。
     */
    public void resize(int cols, int rows) {
        if (terminal == null) return;
        try {
            textBuffer.lock();
            try {
                terminal.resize(new TermSize(cols, rows), RequestOrigin.User);
            } finally {
                textBuffer.unlock();
            }
            session.resize(cols, rows);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] resize failed: {}", t.getMessage());
        }
    }

    public void close() {
        closed = true;
        if (readerThread != null) {
            readerThread.interrupt();
        }
        session.destroy();
    }

    /**
     * 空实现的 TerminalDisplay。
     */
    private static class NoopTerminalDisplay implements TerminalDisplay {
        @Override public void setCursor(int x, int y) {}
        @Override public void setCursorShape(com.jediterm.terminal.CursorShape shape) {}
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
}
