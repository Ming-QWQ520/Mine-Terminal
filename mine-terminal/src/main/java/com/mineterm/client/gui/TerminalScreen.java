package com.mineterm.client.gui;

import com.mineterm.MineTerminal;
import com.mineterm.client.terminal.TerminalKeyAdapter;
import com.mineterm.client.terminal.TerminalSession;
import com.mineterm.client.terminal.TerminalSessionManager;
import com.mineterm.common.MineTerminalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 终端 GUI Screen。
 *
 * 标准 Forge 开发流程：源码用 deobf 名，ForgeGradle reobf 自动映射 SRG 名。
 */
public class TerminalScreen extends Screen {

    private static final int TAB_BAR_HEIGHT = 22;
    private static final int STATUS_BAR_HEIGHT = 18;
    private static final int MARGIN = 6;

    private int cellWidth = 7;
    private int cellHeight = 12;
    private int terminalX, terminalY, terminalW, terminalH;
    private int cols, rows;

    private TerminalRenderer renderer;
    private boolean terminalFocused = true;

    public TerminalScreen() {
        super(Component.translatable("gui.mineterm.title"));
    }

    @Override
    protected void init() {
        try {
            Font font = Minecraft.getInstance().font;
            // 用 'M' 的实际渲染宽度作为等宽单元宽度
            this.cellWidth = font.width("M");
            if (this.cellWidth < 4) this.cellWidth = 7;  // 回退值
            // 行高 = 字体高度 + 2px 间距
            this.cellHeight = font.lineHeight + 2;

            this.terminalX = MARGIN;
            this.terminalY = TAB_BAR_HEIGHT + MARGIN;
            this.terminalW = this.width - MARGIN * 2;
            this.terminalH = this.height - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2;
            this.cols = Math.max(20, this.terminalW / cellWidth);
            this.rows = Math.max(5, this.terminalH / cellHeight);

            TerminalSessionManager mgr = TerminalSessionManager.getInstance();
            if (mgr.getSessionCount() == 0) {
                try {
                    TerminalSession s = mgr.createSession(cols, rows);
                    if (s == null) {
                        MineTerminal.LOGGER.error("[Mine-Terminal] createSession returned null");
                    }
                } catch (Throwable e) {
                    MineTerminal.LOGGER.error("[Mine-Terminal] Failed to create initial session", e);
                }
            } else {
                TerminalSession active = mgr.getActiveSession();
                if (active != null) {
                    try { active.resize(cols, rows); } catch (Throwable ignore) {}
                }
            }

            TerminalSession active = mgr.getActiveSession();
            if (active != null) {
                this.renderer = new TerminalRenderer(active);
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] init() failed", t);
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        int newCols = Math.max(20, (w - MARGIN * 2) / cellWidth);
        int newRows = Math.max(5, (h - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2) / cellHeight);
        if (newCols != cols || newRows != rows) {
            this.cols = newCols;
            this.rows = newRows;
            for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                if (s.isAlive()) {
                    try { s.resize(cols, rows); } catch (Throwable ignore) {}
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            // 背景
            graphics.fill(0, 0, this.width, this.height, 0x80000000);
            graphics.fill(0, 0, this.width, TAB_BAR_HEIGHT, 0x80000000);
            graphics.fill(0, this.height - STATUS_BAR_HEIGHT, this.width, this.height, 0x80000000);

            // 标签栏
            renderTabs(graphics);

            // 终端区
            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null && this.renderer != null) {
                if (this.renderer.session != active) {
                    this.renderer = new TerminalRenderer(active);
                }
                this.renderer.render(graphics, terminalX, terminalY, cellWidth, cellHeight, cols, rows);
                renderStatusBar(graphics, active);
            } else {
                graphics.drawCenteredString(this.font,
                    "No terminal session. Press Ctrl+Shift+T to create one.",
                    this.width / 2, this.height / 2, 0xFF6666);
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] render failed", t);
        }
    }

    private void renderTabs(GuiGraphics graphics) {
        try {
            List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
            int activeIdx = TerminalSessionManager.getInstance().getActiveIndex();
            int x = 8;
            for (int i = 0; i < sessions.size(); i++) {
                TerminalSession s = sessions.get(i);
                String label = s.getName();
                if (!s.isAlive()) label = "[" + label + ": exited]";
                int w = this.font.width(label) + 16;
                boolean activeTab = (i == activeIdx);
                int bg = activeTab ? 0xFF333333 : 0xFF1A1A1A;
                int fg = activeTab ? 0xFFFFFF : 0xAAAAAA;
                graphics.fill(x, 3, x + w, TAB_BAR_HEIGHT - 1, bg);
                graphics.drawString(this.font, label, x + 8, 8, fg, false);
                // 关闭按钮 "×"
                graphics.drawString(this.font, "x", x + w - 10, 8,
                    s.isAlive() ? 0xFF6666 : 0xFF3333, false);
                x += w + 4;
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    private void renderStatusBar(GuiGraphics graphics, TerminalSession s) {
        try {
            String status = String.format(" %s  |  %dx%d  |  %s  |  %s",
                    s.getName(), cols, rows,
                    s.isAlive() ? "running" : "exited",
                    System.getProperty("os.name"));
            graphics.drawString(this.font, status, 6,
                this.height - STATUS_BAR_HEIGHT + 5, 0xC0C0C0, false);

            String warn = "[!] Full OS shell access";
            int w = this.font.width(warn);
            graphics.drawString(this.font, warn, this.width - w - 6,
                this.height - STATUS_BAR_HEIGHT + 5, 0xFFAA44, false);
        } catch (Throwable t) {
            // 静默
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        try {
            boolean ctrlShift = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                    && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (ctrlShift && keyCode == GLFW.GLFW_KEY_Q) {
                forceClose();
                return true;
            }
            if (ctrlShift && keyCode == GLFW.GLFW_KEY_T) {
                try {
                    TerminalSession s = TerminalSessionManager.getInstance().createSession(cols, rows);
                    if (s != null) this.renderer = new TerminalRenderer(s);
                } catch (Throwable t) {
                    MineTerminal.LOGGER.error("[Mine-Terminal] Failed to create new session", t);
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
                if (active != null && active.isAlive()) {
                    TerminalKeyAdapter.handleKeyPressed(active, keyCode, scanCode, modifiers);
                    return true;
                }
                this.onClose();
                return true;
            }

            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null) {
                boolean handled = TerminalKeyAdapter.handleKeyPressed(active, keyCode, scanCode, modifiers);
                if (handled) return true;
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] keyPressed failed", t);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        try {
            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null) {
                boolean handled = TerminalKeyAdapter.handleCharTyped(active, c, modifiers);
                if (handled) return true;
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] charTyped failed", t);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            // 标签栏点击
            if (mouseY < TAB_BAR_HEIGHT) {
                int x = 8;
                List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
                for (int i = 0; i < sessions.size(); i++) {
                    TerminalSession s = sessions.get(i);
                    String label = s.getName();
                    int w = this.font.width(label) + 16;
                    // 关闭按钮区域（右侧 12px）
                    if (mouseX >= x + w - 12 && mouseX < x + w && mouseY >= 3 && mouseY < TAB_BAR_HEIGHT - 1) {
                        // 点击关闭按钮
                        TerminalSessionManager.getInstance().closeSession(i);
                        TerminalSession newActive = TerminalSessionManager.getInstance().getActiveSession();
                        if (newActive != null) {
                            this.renderer = new TerminalRenderer(newActive);
                        } else {
                            this.renderer = null;
                        }
                        return true;
                    }
                    // 标签切换
                    if (mouseX >= x && mouseX < x + w) {
                        TerminalSessionManager.getInstance().setActiveIndex(i);
                        this.renderer = new TerminalRenderer(s);
                        return true;
                    }
                    x += w + 4;
                }
                return false;
            }

            terminalFocused = true;
            return true;
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] mouseClicked failed", t);
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        try {
            if (renderer != null) {
                if (delta > 0) renderer.scrollUp((int) Math.ceil(delta));
                else if (delta < 0) renderer.scrollDown((int) -Math.floor(delta));
            }
        } catch (Throwable t) {
            // 静默
        }
        return true;
    }

    private void forceClose() {
        TerminalSessionManager.getInstance().closeAll();
        this.onClose();
    }

    @Override
    public void onClose() {
        try {
            if (MineTerminalConfig.CLIENT.confirmCloseOnProcessExit.get()) {
                for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                    if (!s.isAlive()) TerminalSessionManager.getInstance().closeSession(s);
                }
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] onClose failed", t);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
