package com.mineterm.client.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * jediterm 的 TtyConnector 适配器：把 PtyTerminalSession 包装成
 * jediterm 可以消费的 TtyConnector 接口。
 *
 * 所有方法都包了 try-catch，避免 native 崩溃传播。
 */
public class PtyProcessTtyConnector implements TtyConnector {

    private final PtyTerminalSession session;
    private volatile boolean connected = false;

    public PtyProcessTtyConnector(PtyTerminalSession session) {
        this.session = session;
    }

    @Override
    public boolean init(Questioner q) {
        try {
            connected = session.isAlive();
        } catch (Throwable t) {
            connected = false;
        }
        return connected;
    }

    @Override
    public void close() {
        try {
            session.destroy();
        } catch (Throwable ignore) {}
        connected = false;
    }

    @Override
    public boolean isConnected() {
        try {
            return connected && session.isAlive();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        try {
            session.resize(termSize.getColumns(), termSize.getRows());
        } catch (Throwable ignore) {}
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        try {
            InputStream is = session.getOutputStreamFromPty();
            if (is == null) return -1;
            byte[] b = new byte[length];
            int n = is.read(b, 0, length);
            if (n <= 0) return n < 0 ? -1 : 0;
            for (int i = 0; i < n; i++) {
                buf[offset + i] = (char) (b[i] & 0xFF);
            }
            return n;
        } catch (Throwable t) {
            // 任何读取异常都返回 -1（EOF），避免 native 崩溃
            return -1;
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        try {
            session.writeToPty(bytes, 0, bytes.length);
        } catch (Throwable ignore) {}
    }

    @Override
    public void write(String str) throws IOException {
        try {
            if (str == null || str.isEmpty()) return;
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            session.writeToPty(bytes, 0, bytes.length);
        } catch (Throwable ignore) {}
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            while (session.isAlive()) {
                Thread.sleep(100);
            }
        } catch (Throwable ignore) {}
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        try {
            InputStream is = session.getOutputStreamFromPty();
            if (is == null) return false;
            return is.available() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String getName() {
        try {
            return session.getSessionName();
        } catch (Throwable t) {
            return "terminal";
        }
    }

    public PtyTerminalSession getSession() { return session; }
}
