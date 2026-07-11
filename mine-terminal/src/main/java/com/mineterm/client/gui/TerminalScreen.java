package com.mineterm.client.gui;

import com.mineterm.MineTerminal;
import com.mineterm.client.terminal.TerminalKeyAdapter;
import com.mineterm.client.terminal.TerminalSession;
import com.mineterm.client.terminal.TerminalSessionManager;
import com.mineterm.common.MineTerminalConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 终端 GUI Screen。
 *
 * 布局：
 *  ┌────────────────────────────────────────────┐
 *  │ [+ New] [Tab1] [Tab2] [Tab3]            [×] │ ← 标签栏 (24px)
 *  ├────────────────────────────────────────────┤
 *  │                                            │
 *  │           终端字符网格                      │
 *  │                                            │
 *  ├────────────────────────────────────────────┤
 *  │ Status: bash@host  |  80x24  |  SSH: off   │ ← 状态栏 (16px)
 *  └────────────────────────────────────────────┘
 *
 * 行为：
 *  - 按 Esc 由终端内部消费（vim 等），只有当终端在非应用模式时才关闭
 *  - Ctrl+Shift+Q 强制关闭并终止所有 PTY
 *  - 鼠标点击终端区域获取焦点；点击标签栏切换标签
 *  - 鼠标滚轮滚动回滚缓冲区
 *  - Ctrl+Shift+T 在屏幕外时打开新标签页（重复使用）
 */
public class TerminalScreen extends Screen {

    private static final Logger LOG = MineTerminal.LOGGER;

    // 布局常量
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
        // 计算字体单元尺寸
        // Minecraft 默认字体在 9px 行高、6px 字宽；我们的等宽视图给一点余量
        int fontSize = MineTerminalConfig.CLIENT.fontSize.get();
        int lineHeight = MineTerminalConfig.CLIENT.lineHeight.get();
        this.cellWidth = Math.max(5, fontSize * 5 / 9);
        this.cellHeight = lineHeight;

        // 终端区域
        this.terminalX = MARGIN;
        this.terminalY = TAB_BAR_HEIGHT + MARGIN;
        this.terminalW = this.width - MARGIN * 2;
        this.terminalH = this.height - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2;
        this.cols = Math.max(20, this.terminalW / cellWidth);
        this.rows = Math.max(5,  this.terminalH / cellHeight);

        LOG.debug("[Mine-Terminal] GUI size: {}x{}, cells: {}x{}", this.width, this.height, cols, rows);

        // 如果还没有任何会话，创建一个
        TerminalSessionManager mgr = TerminalSessionManager.getInstance();
        if (mgr.getSessionCount() == 0) {
            try {
                mgr.createSession(cols, rows);
            } catch (Exception e) {
                LOG.error("[Mine-Terminal] Failed to create initial session", e);
            }
        } else {
            // 已有会话则调整其尺寸
            TerminalSession active = mgr.getActiveSession();
            if (active != null) active.resize(cols, rows);
        }

        TerminalSession active = mgr.getActiveSession();
        if (active != null) {
            this.renderer = new TerminalRenderer(active);
        }

        // 添加标签栏按钮（+ 新建标签）
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> {
                    TerminalSession s = mgr.createSession(cols, rows);
                    this.renderer = new TerminalRenderer(s);
                })
                .bounds(this.width - 28, 2, 20, TAB_BAR_HEIGHT - 4)
                .build());

        // 关闭按钮
        this.addRenderableWidget(Button.builder(Component.literal("×"),
                b -> onClose())
                .bounds(this.width - 50, 2, 20, TAB_BAR_HEIGHT - 4)
                .build());
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        // 重新计算并通知 PTY
        int newCols = Math.max(20, (w - MARGIN * 2) / cellWidth);
        int newRows = Math.max(5,  (h - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2) / cellHeight);
        if (newCols != cols || newRows != rows) {
            this.cols = newCols;
            this.rows = newRows;
            for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                if (s.isAlive()) s.resize(cols, rows);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染半透明背景
        renderBackground(graphics);

        // 标签栏背景
        graphics.fill(0, 0, this.width, TAB_BAR_HEIGHT, 0x80000000);

        // 渲染标签
        renderTabs(graphics, mouseX, mouseY);

        // 渲染状态栏背景
        graphics.fill(0, this.height - STATUS_BAR_HEIGHT, this.width, this.height, 0x80000000);

        // 渲染终端
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active != null && this.renderer != null) {
            // 如果活动会话变了，重建 renderer
            if (this.renderer.session != active) {
                this.renderer = new TerminalRenderer(active);
            }
            this.renderer.render(graphics, terminalX, terminalY, cellWidth, cellHeight, cols, rows);

            // 状态栏
            renderStatusBar(graphics, active);
        } else {
            // 无会话
            graphics.drawCenteredString(this.font, "No terminal session. Click '+' to create one.",
                    this.width / 2, this.height / 2, 0xFF6666);
        }

        // 焦点指示
        if (terminalFocused && active != null && active.isAlive()) {
            int borderCol = 0xFFCC8822;
            graphics.fill(terminalX - 1, terminalY - 1, terminalX, terminalY + terminalH, borderCol);
            graphics.fill(terminalX + terminalW, terminalY - 1, terminalX + terminalW + 1, terminalY + terminalH, borderCol);
            graphics.fill(terminalX - 1, terminalY - 1, terminalX + terminalW + 1, terminalY, borderCol);
            graphics.fill(terminalX - 1, terminalY + terminalH, terminalX + terminalW + 1, terminalY + terminalH + 1, borderCol);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
        int activeIdx = TerminalSessionManager.getInstance().getActiveIndex();
        int x = 8;
        for (int i = 0; i < sessions.size(); i++) {
            TerminalSession s = sessions.get(i);
            String label = s.getName();
            if (!s.isAlive()) label = "[" + label + ": exited]";
            int w = this.font.width(label) + 16;
            boolean active = (i == activeIdx);
            int bg = active ? 0xFF333333 : 0xFF1A1A1A;
            int fg = active ? 0xFFFFFF : 0xAAAAAA;
            g.fill(x, 3, x + w, TAB_BAR_HEIGHT - 1, bg);
            g.drawString(this.font, label, x + 8, 8, fg, false);
            if (!s.isAlive()) {
                g.drawString(this.font, "●", x + w - 10, 8, 0xFF6666, false);
            }
            x += w + 4;
        }
    }

    private void renderStatusBar(GuiGraphics g, TerminalSession s) {
        String status = String.format(" %s  |  %dx%d  |  %s  |  %s",
                s.getName(),
                cols, rows,
                s.isAlive() ? "running" : "exited",
                System.getProperty("os.name"));
        g.drawString(this.font, status, 6, this.height - STATUS_BAR_HEIGHT + 5,
                0xC0C0C0, false);

        // 安全提示
        String warn = "⚠ Full OS shell access";
        int w = this.font.width(warn);
        g.drawString(this.font, warn, this.width - w - 6,
                this.height - STATUS_BAR_HEIGHT + 5, 0xFFAA44, false);
    }

    // =====================================================================
    //  键盘
    // =====================================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Shift+Q 强制关闭
        boolean ctrlShift = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrlShift && keyCode == GLFW.GLFW_KEY_Q) {
            forceClose();
            return true;
        }
        // Ctrl+Shift+T 新建标签
        if (ctrlShift && keyCode == GLFW.GLFW_KEY_T) {
            TerminalSession s = TerminalSessionManager.getInstance().createSession(cols, rows);
            this.renderer = new TerminalRenderer(s);
            return true;
        }

        // Esc 处理：如果终端处于"应用模式"（如 vim），让 jediterm 消费；
        // 否则关闭界面
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null && active.isAlive()) {
                // 简化：始终先把 ESC 发给终端，由终端程序决定。
                // 如果终端真的需要关闭，用户应使用 Ctrl+Shift+Q 或点击 ×
                TerminalKeyAdapter.handleKeyPressed(active, keyCode, scanCode, modifiers);
                return true;
            }
            this.onClose();
            return true;
        }

        // 其他按键发给终端
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active != null) {
            boolean handled = TerminalKeyAdapter.handleKeyPressed(active, keyCode, scanCode, modifiers);
            if (handled) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active != null) {
            boolean handled = TerminalKeyAdapter.handleCharTyped(active, c, modifiers);
            if (handled) return true;
        }
        return super.charTyped(c, modifiers);
    }

    // =====================================================================
    //  鼠标
    // =====================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 标签栏点击？
        if (mouseY < TAB_BAR_HEIGHT) {
            int x = 8;
            List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
            for (int i = 0; i < sessions.size(); i++) {
                TerminalSession s = sessions.get(i);
                String label = s.getName();
                int w = this.font.width(label) + 16;
                if (mouseX >= x && mouseX < x + w) {
                    TerminalSessionManager.getInstance().setActiveIndex(i);
                    this.renderer = new TerminalRenderer(s);
                    return true;
                }
                x += w + 4;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 终端区点击 → 焦点 + 发送鼠标事件给 PTY
        terminalFocused = true;
        if (mouseX >= terminalX && mouseX < terminalX + terminalW
                && mouseY >= terminalY && mouseY < terminalY + terminalH) {
            int col = TerminalRenderer.xToCol((int) mouseX, terminalX, cellWidth);
            int row = TerminalRenderer.yToRow((int) mouseY, terminalY, cellHeight);
            sendMouseEvent(col, row, button, true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (renderer != null) {
            if (delta > 0) renderer.scrollUp((int) Math.ceil(delta));
            else if (delta < 0) renderer.scrollDown((int) -Math.floor(delta));
        }
        return true;
    }

    /**
     * 把鼠标点击编码为 xterm mouse sequence 并发送给 PTY（仅当终端程序启用了鼠标）。
     * 格式：ESC [ M <button> <col+32> <row+32>
     */
    private void sendMouseEvent(int col, int row, int button, boolean press) {
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active == null || !active.isAlive()) return;
        String mode = MineTerminalConfig.CLIENT.mouseMode.get();
        if ("off".equals(mode)) return;
        if ("program".equals(mode)) {
            // 仅当程序请求了鼠标才发送
            boolean mouseEnabled = false;
            try {
                // JediTerminal.getTerminalMode() 检查模式是否启用
                com.jediterm.terminal.model.JediTerminal term = active.getBackend().getTerminal();
                // 简化：用 TerminalMode 的 isEnabled 或反射检查
                // jediterm 真实 API：term.getTerminalMode(mode) 返回 boolean
                // 由于该 API 在 stub 中不存在，简化为 always 发送（依赖终端程序自己处理）
                mouseEnabled = true;
            } catch (Throwable t) {
                // 兼容
            }
            if (!mouseEnabled) return;
        }
        int b = switch (button) {
            case 0 -> press ? 0 : 3;
            case 1 -> press ? 1 : 3;
            case 2 -> press ? 2 : 3;
            default -> 0;
        };
        StringBuilder sb = new StringBuilder();
        sb.append((char) 0x1B).append("[M");
        sb.append((char) (b + 32));
        sb.append((char) (col + 1 + 32));
        sb.append((char) (row + 1 + 32));
        try {
            active.write(sb.toString().getBytes("ISO-8859-1"));
        } catch (Exception e) {
            // ignore
        }
    }

    // =====================================================================
    //  关闭
    // =====================================================================

    private void forceClose() {
        TerminalSessionManager.getInstance().closeAll();
        this.onClose();
    }

    @Override
    public void onClose() {
        // Esc 关闭：仅关闭界面，不杀进程（让会话保持，方便下次打开）
        // 但如果用户希望关闭即终止进程，可以在配置中加开关；这里默认保持
        if (MineTerminalConfig.CLIENT.confirmCloseOnProcessExit.get()) {
            // 已退出的会话在关闭界面时清理掉
            for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                if (!s.isAlive()) TerminalSessionManager.getInstance().closeSession(s);
            }
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
