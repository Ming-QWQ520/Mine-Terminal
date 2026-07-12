package com.mineterm.client.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalColorScheme;
import com.mineterm.common.MineTerminalConfig;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * jediterm 终端后端封装。
 *
 * 不使用 TerminalStarter，自己管理读取线程。
 * 正确处理 UTF-8/GBK 编码，解决中文乱码问题。
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

    // 编码解码器 — 用系统默认编码（Windows 中文系统是 GBK）
    private final CharsetDecoder decoder;
    // 剩余字节缓冲（处理多字节字符跨 read 边界的情况）
    private byte[] leftover = new byte[0];

    private final AtomicBoolean started = new AtomicBoolean(false);

    public JeditermBackend(PtyTerminalSession session, TerminalColorScheme colorScheme) {
        this.session = session;
        this.colorScheme = colorScheme;
        this.connector = new PtyProcessTtyConnector(session);
        // 使用系统默认编码解码（Windows 中文 = GBK, Linux/Mac = UTF-8）
        this.decoder = Charset.defaultCharset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                styleState = new StyleState();
                int fg = colorScheme.getForegroundRGB();
                int bg = colorScheme.getBackgroundRGB();
                styleState.setDefaultStyle(new TextStyle(
                        new TerminalColor((fg >> 16) & 0xFF, (fg >> 8) & 0xFF, fg & 0xFF),
                        new TerminalColor((bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF)
                ));

                int scrollback = MineTerminalConfig.CLIENT.scrollbackLines.get();
                textBuffer = new TerminalTextBuffer(
                        session.getColumns(),
                        session.getRows(),
                        styleState,
                        scrollback
                );

                TerminalDisplay disp = new NoopTerminalDisplay();
                terminal = new JediTerminal(disp, textBuffer, styleState);
                terminal.setModeEnabled(TerminalMode.AutoNewLine, false);
                terminal.setModeEnabled(TerminalMode.CursorKey, true);

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
     * 读取进程输出并送入 JediEmulator 解析。
     * 正确处理多字节编码（UTF-8/GBK）。
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

                // 合并上次剩余的字节
                byte[] combined = new byte[leftover.length + n];
                System.arraycopy(leftover, 0, combined, 0, leftover.length);
                System.arraycopy(buf, 0, combined, leftover.length, n);

                // 用系统默认编码解码（正确处理中文）
                // 留最后 4 字节不解码（防止多字节字符被截断）
                int decodeLen = combined.length;
                if (combined.length > 4) {
                    decodeLen = combined.length - 4;
                }
                ByteBuffer bbuf = ByteBuffer.wrap(combined, 0, decodeLen);
                CharBuffer cbuf = decoder.decode(bbuf);
                char[] chars = new char[cbuf.remaining()];
                cbuf.get(chars);

                // 保存剩余字节
                int decodedBytes = bbuf.position();
                int remaining = combined.length - decodedBytes;
                if (remaining > 0) {
                    leftover = new byte[remaining];
                    System.arraycopy(combined, decodedBytes, leftover, 0, remaining);
                } else {
                    leftover = new byte[0];
                }

                if (chars.length == 0) continue;

                // 送入 JediEmulator 解析 ANSI 序列
                com.jediterm.terminal.ArrayTerminalDataStream dataStream =
                    new com.jediterm.terminal.ArrayTerminalDataStream(chars, 0, chars.length);
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
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        // 处理剩余字节
        if (leftover.length > 0) {
            try {
                ByteBuffer bbuf = ByteBuffer.wrap(leftover);
                CharBuffer cbuf = decoder.decode(bbuf);
                char[] chars = new char[cbuf.remaining()];
                cbuf.get(chars);
                if (chars.length > 0) {
                    com.jediterm.terminal.ArrayTerminalDataStream dataStream =
                        new com.jediterm.terminal.ArrayTerminalDataStream(chars, 0, chars.length);
                    com.jediterm.terminal.emulator.JediEmulator emulator =
                        new com.jediterm.terminal.emulator.JediEmulator(dataStream, terminal);
                    textBuffer.lock();
                    try {
                        while (emulator.hasNext()) emulator.next();
                    } finally {
                        textBuffer.unlock();
                    }
                }
            } catch (Throwable ignore) {}
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
     * 处理键盘输入：直接写入进程 stdin。
     * Windows 上 Backspace 发送 0x08（BS），不是 0x7F（DEL）。
     */
    public void processInputBytes(byte[] data) {
        if (closed) return;
        try {
            // Windows 上把 DEL (0x7F) 转为 BS (0x08)
            if (com.mineterm.client.util.OSUtil.isWindows()) {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == 0x7F) data[i] = 0x08;
                }
            }
            session.writeToPty(data, 0, data.length);
        } catch (Throwable t) {
            LOG.warn("[Mine-Terminal] Failed to write input: {}", t.getMessage());
        }
    }

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
