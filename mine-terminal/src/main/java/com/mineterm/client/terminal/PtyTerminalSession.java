package com.mineterm.client.terminal;

import com.mineterm.MineTerminal;
import com.mineterm.client.util.OSUtil;
import com.mineterm.common.MineTerminalConfig;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.pty4j.unix.PtyHelpers;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

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
 * 职责：
 *   1. 使用 pty4j 创建伪终端进程（Linux/macOS 直接 fork+exec；Windows 走 ConPTY/WinPTY）
 *   2. 暴露输入/输出流给 jediterm 的 TtyConnector
 *   3. 监听进程退出
 *   4. 支持动态调整 PTY 窗口大小（TIOCSWINSZ）
 *   5. 提供安全销毁（SIGHUP / destroyForcibly）以避免僵尸进程
 *
 * jediterm 不直接懂 PtyProcess，需要通过 TtyConnector 适配；该适配在
 * {@link PtyProcessTtyConnector} 中实现。本类负责"管理 PTY 进程本身"。
 */
public class PtyTerminalSession {

    private static final Logger LOG = MineTerminal.LOGGER;

    private final String sessionName;
    private PtyProcess process;
    private OutputStream ptyInput;
    private InputStream ptyOutput;

    private final AtomicBoolean alive = new AtomicBoolean(false);
    private volatile boolean exitFlagged = false;

    // 终端尺寸
    private volatile int columns;
    private volatile int rows;

    // 退出回调
    @Nullable
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

    /**
     * 启动 PTY 进程。
     *
     * @throws IOException 启动失败时抛出
     */
    public synchronized void start() throws IOException {
        if (alive.get()) {
            throw new IllegalStateException("PTY session already started: " + sessionName);
        }

        MineTerminalConfig.Client cfg = MineTerminalConfig.CLIENT;
        // 优先用配置文件指定的 shell，否则自动检测
        String shellPath = cfg.shellCommand.get();
        if (shellPath == null || shellPath.isBlank()) {
            shellPath = OSUtil.detectDefaultShell();
        }
        String argsRaw = cfg.shellArgs.get();
        String cwd = cfg.initialWorkingDir.get();
        if (cwd == null || cwd.isBlank()) cwd = OSUtil.detectDefaultWorkingDir();

        // 组装命令数组
        String[] command;
        if (argsRaw == null || argsRaw.isBlank()) {
            command = new String[]{shellPath};
        } else {
            String[] parts = argsRaw.trim().split("\\s+");
            command = new String[parts.length + 1];
            command[0] = shellPath;
            System.arraycopy(parts, 0, command, 1, parts.length);
        }

        // 环境变量：复制当前进程环境，覆盖 TERM
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", cfg.termEnv.get());
        // 提示全屏程序我们在 PTY 中
        env.put("TERM_PROGRAM", "mine-terminal");
        env.put("COLORTERM", "truecolor");
        // 在 Windows 上，确保 ConPTY 路径生效
        if (OSUtil.isWindows()) {
            env.putIfAbsent("ConEmuANSI", "ON");
        }

        try {
            PtyProcessBuilder pb = new PtyProcessBuilder(command)
                    .setDirectory(cwd)
                    .setEnvironment(env)
                    .setInitialColumns(columns)
                    .setInitialRows(rows)
                    .setConsole(false);     // 关键：false 表示这是 PTY 不是 console
            // Windows 上 pty4j 默认会自动选择 ConPTY/WinPTY

            this.process = pb.start();
            this.ptyInput = process.getOutputStream();   // 写入 PTY = 进程的 stdin
            this.ptyOutput = process.getInputStream();   // 读取 PTY = 进程的 stdout+stderr
            this.alive.set(true);

            // 启动监听线程，等待进程退出
            Thread watcher = new Thread(this::watchProcessExit,
                    "mine-terminal-pty-watcher-" + sessionName);
            watcher.setDaemon(true);
            watcher.start();

            if (MineTerminalConfig.COMMON.logCommandExec.get()) {
                LOG.info("[Mine-Terminal] PTY started: session={}, cmd={}, cwd={}, size={}x{}",
                        sessionName, String.join(" ", command), cwd, columns, rows);
            } else {
                LOG.debug("[Mine-Terminal] PTY started: session={}", sessionName);
            }
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            // pty4j 的原生库加载失败
            throw new IOException("Failed to load pty4j native library. " +
                    "Make sure the bundled natives match your platform. Cause: " + e.getMessage(), e);
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

    /**
     * 写入数据到 PTY（即发送到子进程的 stdin）。
     * 字符串按 UTF-8 编码。
     */
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

    /**
     * 同步调整 PTY 窗口尺寸。对应 ioctl(TIOCSWINSZ)。
     * pty4j 内部已封装为 setWinSize / setWindowSize。
     */
    public synchronized void resize(int newColumns, int newRows) {
        if (process == null) return;
        try {
            this.columns = newColumns;
            this.rows = newRows;
            process.setWinSize(new WinSize(newColumns, newRows));
            LOG.debug("[Mine-Terminal] PTY resized: {}x{}", newColumns, newRows);
        } catch (Exception e) {
            LOG.warn("[Mine-Terminal] Failed to resize PTY: {}", e.getMessage());
        }
    }

    public int getColumns() { return columns; }
    public int getRows()    { return rows; }
    public String getSessionName() { return sessionName; }

    public boolean isAlive() { return alive.get() && !exitFlagged; }
    public boolean isExitFlagged() { return exitFlagged; }

    /**
     * 优雅关闭：先 SIGHUP，再 destroyForcibly。
     * 必须调用以避免僵尸进程。
     */
    public synchronized void destroy() {
        if (process == null) return;
        alive.set(false);
        try {
            // 优先发送 SIGHUP（终端挂断），让子进程清理资源
            if (OSUtil.isLinux() || OSUtil.isMac()) {
                try {
                    long pid = getUnixPid(process);
                    if (pid > 0) {
                        // pty4j 真实 API：PtyHelpers.signal(int pid, int signal)
                        // SIGHUP 是常量
                        com.pty4j.unix.PtyHelpers.signal((int) pid, com.pty4j.unix.PtyHelpers.SIGHUP);
                    }
                } catch (Throwable t) {
                    // ignore — fallback to destroyForcibly
                }
            }
            // 给 200ms 让子进程优雅退出
            try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
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

    /**
     * 在 Linux/macOS 上从 PtyProcess 取出底层 pid。
     * pty4j 没有公开 API，通过反射尝试获取。
     */
    private static long getUnixPid(PtyProcess p) {
        try {
            // PtyProcess 在 unix 上是 com.pty4j.unix.PtyProcess
            try {
                java.lang.reflect.Method m = p.getClass().getMethod("getPid");
                Object r = m.invoke(p);
                if (r instanceof Number) return ((Number) r).longValue();
            } catch (NoSuchMethodException ignore) {}
            // 回退：通过 Process.pid() (JDK 9+)
            java.lang.reflect.Method m = Process.class.getMethod("pid");
            Object r = m.invoke(p);
            if (r instanceof Number) return ((Number) r).longValue();
        } catch (Throwable t) {
            // ignore
        }
        return -1;
    }
}
