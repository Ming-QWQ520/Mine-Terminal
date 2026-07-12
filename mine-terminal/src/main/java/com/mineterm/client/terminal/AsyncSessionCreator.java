package com.mineterm.client.terminal;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * 异步会话创建器。
 * 在后台线程创建终端会话，避免阻塞渲染线程。
 *
 * 用反射访问 MC 类，避免 SRG 混淆问题。
 */
public class AsyncSessionCreator {

    private static volatile boolean creating = false;
    private static volatile boolean monitorStarted = false;
    private static Method getInstanceMethod = null;
    private static Field screenField = null;

    public static synchronized void startMonitor() {
        if (monitorStarted) return;
        monitorStarted = true;

        // 预解析反射方法
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            try {
                getInstanceMethod = mcClass.getMethod("m_91087_");
            } catch (NoSuchMethodException e) {
                getInstanceMethod = mcClass.getMethod("getInstance");
            }
            try {
                screenField = mcClass.getField("f_91080_");
            } catch (NoSuchFieldException e) {
                screenField = mcClass.getField("screen");
            }
        } catch (Throwable t) {
            com.mineterm.MineTerminal.LOGGER.error("[Mine-Terminal] AsyncSessionCreator reflection init failed", t);
            return;
        }

        Thread monitor = new Thread(() -> {
            com.mineterm.MineTerminal.LOGGER.info("[Mine-Terminal] AsyncSessionCreator monitor started.");
            while (true) {
                try {
                    Thread.sleep(500);
                    if (creating) continue;

                    // 用反射检查当前 Screen
                    Object mc = getInstanceMethod.invoke(null);
                    if (mc == null) continue;
                    Object screen = screenField.get(mc);
                    if (screen == null) continue;

                    String screenClassName = screen.getClass().getName();
                    if (!screenClassName.contains("TerminalScreen")) continue;

                    TerminalSessionManager mgr = TerminalSessionManager.getInstance();
                    if (mgr.getSessionCount() > 0) continue;

                    creating = true;
                    com.mineterm.MineTerminal.LOGGER.info("[Mine-Terminal] Starting async session creation...");

                    Thread creator = new Thread(() -> {
                        try {
                            TerminalSession s = mgr.createSession(80, 24);
                            if (s != null) {
                                com.mineterm.MineTerminal.LOGGER.info("[Mine-Terminal] Async session created successfully.");
                            }
                        } catch (Throwable ex) {
                            com.mineterm.MineTerminal.LOGGER.error("[Mine-Terminal] Async session creation failed.", ex);
                        } finally {
                            creating = false;
                        }
                    }, "mine-terminal-session-creator");
                    creator.setDaemon(true);
                    creator.start();

                } catch (Throwable t) {
                    // 静默
                }
            }
        }, "mine-terminal-session-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }
}
