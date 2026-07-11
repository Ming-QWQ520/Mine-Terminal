package com.mineterm.client.terminal;

import com.mineterm.client.gui.TerminalColorScheme;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个完整的终端会话 = PTY 进程 + jediterm 后端 + 配色方案 + 元数据。
 *
 * 这是 {@link TerminalSessionManager} 管理的最小单元。
 * 一个会话对应一个终端标签页。
 */
public class TerminalSession {

    private final String id;
    private String name;
    private final PtyTerminalSession pty;
    private final JeditermBackend backend;
    private final TerminalColorScheme colorScheme;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean disposed = false;

    public TerminalSession(String name, int cols, int rows, TerminalColorScheme scheme) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.colorScheme = scheme;
        this.pty = new PtyTerminalSession(name, cols, rows);
        this.backend = new JeditermBackend(pty, scheme);
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            pty.start();
            backend.start();
            // 进程退出时通知
            pty.setExitListener(code -> {
                // 标记会话已结束，由 GUI 显示"进程已退出"提示
                onProcessExited(code);
            });
        }
    }

    private void onProcessExited(int code) {
        // 简单日志；GUI 层会在 TerminalScreen 中检测 isAlive 并显示提示
    }

    public void write(byte[] data) {
        backend.processInputBytes(data);
    }

    public void resize(int cols, int rows) {
        backend.resize(cols, rows);
    }

    public boolean isAlive() {
        return !disposed && pty.isAlive();
    }

    public boolean isExitFlagged() {
        return pty.isExitFlagged();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public PtyTerminalSession getPty() { return pty; }
    public JeditermBackend getBackend() { return backend; }
    public TerminalColorScheme getColorScheme() { return colorScheme; }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            backend.close();
        } catch (Throwable t) {
            // ignore
        }
    }
}
