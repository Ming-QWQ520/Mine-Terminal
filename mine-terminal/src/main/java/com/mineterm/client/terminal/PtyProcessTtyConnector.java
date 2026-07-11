package com.mineterm.client.terminal;

import com.jediterm.core.util.TerminalSize;
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
 * jediterm 会通过这个 connector：
 *   - read(buf): 读取 PTY 输出，作为 ANSI 流解析
 *   - write(str): 将用户输入（已转换为转义序列）发送到 PTY
 *   - resize(size): 通知 PTY 调整窗口尺寸
 *   - isConnected(): 检查 PTY 是否仍在运行
 *   - close(): 关闭 PTY
 */
public class PtyProcessTtyConnector implements TtyConnector {

    private final PtyTerminalSession session;
    private volatile boolean connected = false;

    public PtyProcessTtyConnector(PtyTerminalSession session) {
        this.session = session;
    }

    @Override
    public boolean init() {
        // 实际的 PTY 启动已由 PtyTerminalSession.start() 完成，
        // 这里只标记连接状态。
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
    public void resize(@NotNull TerminalSize termSize) {
        session.resize(termSize.getColumns(), termSize.getRows());
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        // 从 PTY 字节流解码为字符。jediterm 期望字符流。
        // 为简化，我们逐字节读取并用 ISO-8859-1 → char 的方式
        // （jediterm 内部会重新组合 UTF-8 序列）
        InputStream is = session.getOutputStreamFromPty();
        if (is == null) return -1;
        byte[] b = new byte[length];
        int n = is.read(b, 0, length);
        if (n <= 0) return n < 0 ? -1 : 0;
        // 简单字节→char 转换：jediterm 内部维护 UTF-8 解码状态机
        for (int i = 0; i < n; i++) {
            buf[offset + i] = (char) (b[i] & 0xFF);
        }
        return n;
    }

    @Override
    public void write(String str) throws IOException {
        if (str == null || str.isEmpty()) return;
        session.writeToPty(str.getBytes(StandardCharsets.UTF_8), 0,
                str.getBytes(StandardCharsets.UTF_8).length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        session.writeToPty(bytes, 0, bytes.length);
    }

    @Override
    public String getName() {
        return session.getSessionName();
    }

    public PtyTerminalSession getSession() { return session; }
}
