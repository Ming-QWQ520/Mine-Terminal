package com.mineterm.client.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * jediterm 的 TtyConnector 适配器：把 PtyTerminalSession 包装成
 * jediterm 可以消费的 TtyConnector 接口。
 *
 * 真实 jediterm-core 3.73 的 TtyConnector 接口：
 *   - int read(char[], int, int) throws IOException    读取 PTY 输出
 *   - void write(byte[]) throws IOException            写入 PTY（用户输入）
 *   - void write(String) throws IOException            写入 PTY（用户输入）
 *   - boolean isConnected()                            PTY 是否仍在运行
 *   - void resize(TermSize)                            调整 PTY 窗口尺寸
 *   - int waitFor() throws InterruptedException        等待进程退出
 *   - boolean ready() throws IOException               是否有数据可读
 *   - String getName()                                 会话名
 *   - void close()                                     关闭 PTY
 *   - boolean init(Questioner)                         初始化（默认实现返回 isConnected）
 */
public class PtyProcessTtyConnector implements TtyConnector {

    private final PtyTerminalSession session;
    private volatile boolean connected = false;

    public PtyProcessTtyConnector(PtyTerminalSession session) {
        this.session = session;
    }

    @Override
    public boolean init(Questioner q) {
        // PTY 已由 PtyTerminalSession.start() 启动，这里只标记连接状态
        connected = session.isAlive();
        return connected;
    }

    @Override
    public void close() {
        session.destroy();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected && session.isAlive();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        session.resize(termSize.getColumns(), termSize.getRows());
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        // 从 PTY 字节流解码为字符。jediterm 期望字符流。
        // 逐字节读取并用 ISO-8859-1 → char 的方式
        // （jediterm 内部维护 UTF-8 解码状态机）
        InputStream is = session.getOutputStreamFromPty();
        if (is == null) return -1;
        byte[] b = new byte[length];
        int n = is.read(b, 0, length);
        if (n <= 0) return n < 0 ? -1 : 0;
        for (int i = 0; i < n; i++) {
            buf[offset + i] = (char) (b[i] & 0xFF);
        }
        return n;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        session.writeToPty(bytes, 0, bytes.length);
    }

    @Override
    public void write(String str) throws IOException {
        if (str == null || str.isEmpty()) return;
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        session.writeToPty(bytes, 0, bytes.length);
    }

    @Override
    public int waitFor() throws InterruptedException {
        // 等待 PTY 进程退出；返回退出码
        // 简化：直接轮询直到不 alive
        while (session.isAlive()) {
            Thread.sleep(100);
        }
        return 0; // 退出码未知，jediterm 不强依赖
    }

    @Override
    public boolean ready() throws IOException {
        InputStream is = session.getOutputStreamFromPty();
        if (is == null) return false;
        try {
            return is.available() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return session.getSessionName();
    }

    public PtyTerminalSession getSession() { return session; }
}
