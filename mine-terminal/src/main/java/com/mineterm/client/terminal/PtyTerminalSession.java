package com.mineterm.client.terminal;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalColorScheme;
import com.mineterm.common.MineTerminalConfig;
import com.mineterm.client.util.OSUtil;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PTY 进程与 jediterm 之间的桥接层。
 *
 * ★ Windows 崩溃修复 ★
 * setConsole(false) 在 Windows 上使用 ConPTY，可能导致 native 崩溃。
 * 改为 setConsole(true) 使用 WinPTY（更稳定）。
 */
public class PtyTerminalSession {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final String sessionName;
    private PtyProcess process;
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
            throw new IllegalStateException("PTY session already started: " + sessionName);
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
        if (argsRaw == null || argsRaw.isBlank()) {
            command = new String[]{shellPath};
        } else {
            String[] parts = argsRaw.trim().split("\\s+");
            command = new String[parts.length + 1];
            command[0] = shellPath;
            System.arraycopy(parts, 0, command, 1, parts.length);
        }

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", cfg.termEnv.get());
        env.put("TERM_PROGRAM", "mine-terminal");
        env.put("COLORTERM", "truecolor");
        if (OSUtil.isWindows()) {
            env.putIfAbsent("ConEmuANSI", "ON");
        }

        try {
            // ★ Windows 用 setConsole(true) = WinPTY（避免 ConPTY native 崩溃）
            // ★ Linux/Mac 用 setConsole(false) = 原生 PTY
            PtyProcessBuilder pb = new PtyProcessBuilder(command)
                    .setDirectory(cwd)
                    .setEnvironment(env)
                    .setInitialColumns(columns)
                    .setInitialRows(rows)
                    .setConsole(OSUtil.isWindows());

            this.process = pb.start();
            this.ptyInput = process.getOutputStream();
            this.ptyOutput = process.getInputStream();
            this.alive.set(true);

            Thread watcher = new Thread(this::watchProcessExit,
                    "mine-terminal-pty-watcher-" + sessionName);
            watcher.setDaemon(true);
            watcher.start();

            LOG.debug("[Mine-Terminal] PTY started: session={}, console={}", sessionName, OSUtil.isWindows());
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new IOException("Failed to load pty4j native library: " + e.getMessage(), e);
        }
    }

    private void watchProcessExit() {
        try {
            int code = process.waitFor();
            exitFlagged = true;
            LOG.info("[Mine-Terminal] PTY exited: session={}, code={}", sessionName, code);
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
        if (process == null) return;
        try {
            this.columns = newColumns;
            this.rows = newRows;
            process.setWinSize(new WinSize(newColumns, newRows));
        } catch (Exception e) {
            LOG.warn("[Mine-Terminal] Failed to resize PTY: {}", e.getMessage());
        }
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
            LOG.info("[Mine-Terminal] PTY session destroyed: {}", sessionName);
        }
    }
}
