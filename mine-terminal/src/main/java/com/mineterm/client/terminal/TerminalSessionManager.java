package com.mineterm.client.terminal;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalColorScheme;
import com.mineterm.common.MineTerminalConfig;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多会话管理器（单例）。
 *
 * - 维护所有活跃的 {@link TerminalSession}
 * - 提供创建、关闭、切换会话 API
 * - 模组关闭时确保所有 PTY 都被清理，避免僵尸进程
 * - 当前活动会话由 GUI Screen 持有引用，但创建/销毁必须经过本管理器
 */
public class TerminalSessionManager {

    private static final Logger LOG = MineTerminal.LOGGER;
    private static final TerminalSessionManager INSTANCE = new TerminalSessionManager();

    private final List<TerminalSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger nameCounter = new AtomicInteger(1);
    private volatile int activeIndex = -1;

    private TerminalSessionManager() {}

    public static TerminalSessionManager getInstance() { return INSTANCE; }

    public List<TerminalSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    public int getSessionCount() { return sessions.size(); }

    public TerminalSession getActiveSession() {
        if (activeIndex < 0 || activeIndex >= sessions.size()) return null;
        return sessions.get(activeIndex);
    }

    public int getActiveIndex() { return activeIndex; }

    public void setActiveIndex(int i) {
        if (i >= 0 && i < sessions.size()) activeIndex = i;
    }

    /**
     * 创建并启动新会话。
     */
    public TerminalSession createSession(int cols, int rows) {
        String name = "Terminal-" + nameCounter.getAndIncrement();
        TerminalColorScheme scheme = TerminalColorScheme.fromConfig();
        TerminalSession s = new TerminalSession(name, cols, rows, scheme);
        try {
            s.start();
        } catch (Exception e) {
            LOG.error("[Mine-Terminal] Failed to start session: {}", e.getMessage(), e);
            s.dispose();
            throw new RuntimeException("Failed to start terminal session", e);
        }
        sessions.add(s);
        activeIndex = sessions.size() - 1;
        LOG.info("[Mine-Terminal] Session created: {} (total={})", name, sessions.size());
        return s;
    }

    /**
     * 关闭指定会话。
     */
    public void closeSession(int index) {
        if (index < 0 || index >= sessions.size()) return;
        TerminalSession s = sessions.remove(index);
        if (s != null) {
            LOG.info("[Mine-Terminal] Closing session: {}", s.getName());
            s.dispose();
        }
        if (activeIndex >= sessions.size()) {
            activeIndex = sessions.size() - 1;
        }
    }

    public void closeSession(TerminalSession s) {
        int i = sessions.indexOf(s);
        if (i >= 0) closeSession(i);
    }

    /**
     * 关闭所有会话——在 Minecraft 关闭或 Screen 关闭时调用。
     */
    public void closeAll() {
        LOG.info("[Mine-Terminal] Closing all sessions ({})", sessions.size());
        for (TerminalSession s : new ArrayList<>(sessions)) {
            try { s.dispose(); } catch (Throwable t) { LOG.warn("dispose error", t); }
        }
        sessions.clear();
        activeIndex = -1;
    }

    public TerminalSession getSession(int index) {
        if (index < 0 || index >= sessions.size()) return null;
        return sessions.get(index);
    }
}
