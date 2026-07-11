package com.mineterm.client.util;

/**
 * 操作系统工具类，用于决定 PTY 启动方式。
 *
 *  - Linux/macOS: 直接使用 pty4j 的 PtyProcessBuilder 启动 shell
 *  - Windows: 优先使用 ConPTY (Windows 10 1809+)，否则回退到 WinPTY
 *
 * 基于 system property 检测，不引入额外依赖。
 */
public final class OSUtil {

    public enum Platform {
        LINUX,
        MACOS,
        WINDOWS,
        UNKNOWN
    }

    private static final Platform CURRENT;
    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            CURRENT = Platform.WINDOWS;
        } else if (os.contains("mac") || os.contains("darwin")) {
            CURRENT = Platform.MACOS;
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            CURRENT = Platform.LINUX;
        } else {
            CURRENT = Platform.UNKNOWN;
        }
    }

    private OSUtil() {}

    public static Platform current() { return CURRENT; }
    public static boolean isWindows() { return CURRENT == Platform.WINDOWS; }
    public static boolean isMac()     { return CURRENT == Platform.MACOS; }
    public static boolean isLinux()   { return CURRENT == Platform.LINUX; }

    /**
     * 自动检测系统默认 shell。
     *  - Linux/macOS: $SHELL 环境变量，回退到 /bin/bash
     *  - Windows: 优先 powershell，回退到 cmd
     */
    public static String detectDefaultShell() {
        switch (CURRENT) {
            case LINUX:
            case MACOS: {
                String sh = System.getenv("SHELL");
                if (sh != null && !sh.isEmpty()) return sh;
                return "/bin/bash";
            }
            case WINDOWS: {
                // 优先 powershell
                return "powershell.exe";
            }
            default:
                return "/bin/sh";
        }
    }

    /**
     * 返回默认初始工作目录（用户主目录）。
     */
    public static String detectDefaultWorkingDir() {
        String home = System.getenv("HOME");
        if (home == null || home.isEmpty()) home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) home = ".";
        return home;
    }
}
