package com.mineterm.client.terminal;

import com.mineterm.MineTerminal;
import com.mineterm.client.util.OSUtil;
import com.mineterm.common.MineTerminalConfig;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 终端会话 — 使用 Java 原生 ProcessBuilder 代替 pty4j。
 *
 * ★ 彻底修复 native 崩溃 ★
 *
 * pty4j 在 Windows 上无论 ConPTY 还是 WinPTY 都会导致 JVM native 崩溃。
 * 改用 Java 原生 ProcessBuilder，虽然没有 PTY 支持（不能运行 vim/htop
 * 等全屏交互程序），但能稳定运行基本命令（dir, echo, python 等）不崩溃。
 *
 * 后续如果需要 PTY 支持，可以考虑：
 *   - 用 WSL 作为后端
 *   - 用 JNA/JNI 自己封装 Windows ConPTY API
 *   - 等待 pty4j 修复 Windows 崩溃问题
 */
public class PtyTerminalSession {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final String sessionName;
    private Process process;
    private OutputStream ptyInput;
    private InputStream ptyOutput;

    private final AtomicBoolean alive = new AtomicBoolean(false);
    private volatile boolean exitFlagged = false;

    private volatile int columns;
    private volatile int rows;

    private ProcessExitListener exitListener;

    public PtyTerminalSession(String sessionName, int columns, int rows) {
        this.sessionName = sessionName;
        this.columns = columns;
        this.rows = rows;
    }

    public interface ProcessExitListener {
        void onExit(int exitCode);
    }

    public void setExitListener(ProcessExitListener l) { this.exitListener = l; }

    public synchronized void start() throws IOException {
        if (alive.get()) {
            throw new IllegalStateException("Session already started: " + sessionName);
        }

        MineTerminalConfig.Client cfg = MineTerminalConfig.CLIENT;
        String shellPath = cfg.shellCommand.get();
        if (shellPath == null || shellPath.isBlank()) {
            shellPath = OSUtil.detectDefaultShell();
        }
        String argsRaw = cfg.shellArgs.get();
        String cwd = cfg.initialWorkingDir.get();
        if (cwd == null || cwd.isBlank()) cwd = OSUtil.detectDefaultWorkingDir();

        String[] command;
        // Windows 上 PowerShell 需要 -NoExit 否则会立即退出
        if (OSUtil.isWindows() && shellPath.toLowerCase().contains("powershell")) {
            if (argsRaw == null || argsRaw.isBlank()) {
                command = new String[]{shellPath, "-NoExit", "-NoLogo"};
            } else {
                String[] parts = argsRaw.trim().split("\\s+");
                command = new String[parts.length + 3];
                command[0] = shellPath;
                command[1] = "-NoExit";
                command[2] = "-NoLogo";
                System.arraycopy(parts, 0, command, 3, parts.length);
            }
        } else if (argsRaw == null || argsRaw.isBlank()) {
            command = new String[]{shellPath};
        } else {
            String[] parts = argsRaw.trim().split("\\s+");
            command = new String[parts.length + 1];
            command[0] = shellPath;
            System.arraycopy(parts, 0, command, 1, parts.length);
        }

        // 用 ProcessBuilder 代替 PtyProcessBuilder
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new java.io.File(cwd));
        pb.redirectErrorStream(true); // 合并 stderr 到 stdout

        // 环境变量
        Map<String, String> env = pb.environment();
        env.put("TERM", cfg.termEnv.get());
        env.put("TERM_PROGRAM", "mine-terminal");
        env.put("COLORTERM", "truecolor");
        if (OSUtil.isWindows()) {
            env.putIfAbsent("ConEmuANSI", "ON");
        }

        this.process = pb.start();
        this.ptyInput = process.getOutputStream();  // 写入进程 stdin
        this.ptyOutput = process.getInputStream();  // 读取进程 stdout+stderr
        this.alive.set(true);

        // 启动监听线程
        Thread watcher = new Thread(this::watchProcessExit,
                "mine-terminal-pty-watcher-" + sessionName);
        watcher.setDaemon(true);
        watcher.start();

        LOG.debug("[Mine-Terminal] Process started: session={}", sessionName);
    }

    private void watchProcessExit() {
        try {
            int code = process.waitFor();
            exitFlagged = true;
            LOG.info("[Mine-Terminal] Process exited: session={}, code={}", sessionName, code);
            if (exitListener != null) {
                exitListener.onExit(code);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            alive.set(false);
        }
    }

    public synchronized void writeToPty(String data) throws IOException {
        if (!alive.get() || ptyInput == null) return;
        ptyInput.write(data.getBytes(StandardCharsets.UTF_8));
        ptyInput.flush();
    }

    public synchronized void writeToPty(byte[] data, int off, int len) throws IOException {
        if (!alive.get() || ptyInput == null) return;
        ptyInput.write(data, off, len);
        ptyInput.flush();
    }

    public InputStream getOutputStreamFromPty() { return ptyOutput; }
    public OutputStream getInputStreamToPty()   { return ptyInput; }

    public synchronized void resize(int newColumns, int newRows) {
        this.columns = newColumns;
        this.rows = newRows;
        // ProcessBuilder 没有 resize 功能，忽略
    }

    public int getColumns() { return columns; }
    public int getRows()    { return rows; }
    public String getSessionName() { return sessionName; }

    public boolean isAlive() { return alive.get() && !exitFlagged; }
    public boolean isExitFlagged() { return exitFlagged; }

    public synchronized void destroy() {
        if (process == null) return;
        alive.set(false);
        try {
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } finally {
            try { if (ptyInput != null) ptyInput.close(); } catch (IOException ignore) {}
            try { if (ptyOutput != null) ptyOutput.close(); } catch (IOException ignore) {}
            process = null;
            ptyInput = null;
            ptyOutput = null;
            LOG.info("[Mine-Terminal] Session destroyed: {}", sessionName);
        }
    }
}
