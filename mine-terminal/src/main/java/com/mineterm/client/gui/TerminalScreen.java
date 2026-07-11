package com.mineterm.client.gui;

import com.mineterm.MineTerminal;
import com.mineterm.client.terminal.TerminalKeyAdapter;
import com.mineterm.client.terminal.TerminalSession;
import com.mineterm.client.terminal.TerminalSessionManager;
import com.mineterm.client.util.MCReflect;
import com.mineterm.common.MineTerminalConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 终端 GUI Screen。
 *
 * 关键设计：本类继承 MC 的 Screen，但所有 MC 类方法/字段访问都通过
 * {@link MCReflect} 反射，避免 production 环境 SRG 混淆导致的 NoSuchMethodError。
 *
 * 只保留 Screen 的以下不混淆接口：
 *   - 字段：width, height, font（Forge 通过 AT 让它们 public，字段名稳定）
 *   - 构造函数：super(Component) — 但 Component.translatable 也被混淆，所以用反射
 *   - 方法：render(), keyPressed(), charTyped(), mouseClicked(), mouseScrolled(), onClose()
 *   （这些方法签名是 MC 反射调用的，方法名被 SRG 混淆但 Forge 通过 ASM 调度，
 *    我们用 @Override 标注，编译器生成正确符号，Forge 在运行时按 SRG 名查找）
 *
 * 但是！@Override 的方法名在 production 也会被 SRG 混淆为 m_xxx_，
 * Forge 在调用 Screen.render 等方法时是用 SRG 名查找的，所以 @Override 方法
 * 不会出问题（编译器生成的字节码方法名会被 reobf 映射）。
 *
 * 真正的问题在于：我们在方法体内调用 MC 类方法（如 Component.translatable、
 * graphics.fill、font.width 等）会出问题。这些都改用 MCReflect 反射。
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
        // 不能用 super(Component.translatable(...)) — translatable 会被 SRG 混淆
        // 用反射构造 Component 然后传给 super
        super((Component) MCReflect.componentTranslatable("gui.mineterm.title"));
    }

    @Override
    protected void init() {
        int fontSize = MineTerminalConfig.CLIENT.fontSize.get();
        int lineHeight = MineTerminalConfig.CLIENT.lineHeight.get();
        this.cellWidth = Math.max(5, fontSize * 5 / 9);
        this.cellHeight = lineHeight;

        this.terminalX = MARGIN;
        this.terminalY = TAB_BAR_HEIGHT + MARGIN;
        this.terminalW = this.width - MARGIN * 2;
        this.terminalH = this.height - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2;
        this.cols = Math.max(20, this.terminalW / cellWidth);
        this.rows = Math.max(5, this.terminalH / cellHeight);

        TerminalSessionManager mgr = TerminalSessionManager.getInstance();
        if (mgr.getSessionCount() == 0) {
            try {
                mgr.createSession(cols, rows);
            } catch (Exception e) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to create initial session", e);
            }
        } else {
            TerminalSession active = mgr.getActiveSession();
            if (active != null) active.resize(cols, rows);
        }

        TerminalSession active = mgr.getActiveSession();
        if (active != null) {
            this.renderer = new TerminalRenderer(active);
        }
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        int newCols = Math.max(20, (w - MARGIN * 2) / cellWidth);
        int newRows = Math.max(5, (h - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2) / cellHeight);
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
        // 渲染半透明背景（用反射 fill）
        MCReflect.ggFill(graphics, 0, 0, this.width, this.height, 0x80000000);
        // 标签栏背景
        MCReflect.ggFill(graphics, 0, 0, this.width, TAB_BAR_HEIGHT, 0x80000000);
        // 状态栏背景
        MCReflect.ggFill(graphics, 0, this.height - STATUS_BAR_HEIGHT, this.width, this.height, 0x80000000);

        // 渲染标签
        renderTabs(graphics, mouseX, mouseY);

        // 渲染终端
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active != null && this.renderer != null) {
            if (this.renderer.session != active) {
                this.renderer = new TerminalRenderer(active);
            }
            this.renderer.render(graphics, terminalX, terminalY, cellWidth, cellHeight, cols, rows);
            renderStatusBar(graphics, active);
        } else {
            MCReflect.ggDrawCenteredString(graphics, this.font,
                "No terminal session. Click '+' to create one.",
                this.width / 2, this.height / 2, 0xFF6666);
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
        int activeIdx = TerminalSessionManager.getInstance().getActiveIndex();
        int x = 8;
        for (int i = 0; i < sessions.size(); i++) {
            TerminalSession s = sessions.get(i);
            String label = s.getName();
            if (!s.isAlive()) label = "[" + label + ": exited]";
            int w = MCReflect.fontWidth(this.font, label) + 16;
            boolean activeTab = (i == activeIdx);
            int bg = activeTab ? 0xFF333333 : 0xFF1A1A1A;
            int fg = activeTab ? 0xFFFFFF : 0xAAAAAA;
            MCReflect.ggFill(graphics, x, 3, x + w, TAB_BAR_HEIGHT - 1, bg);
            MCReflect.ggDrawString(graphics, this.font, label, x + 8, 8, fg, false);
            if (!s.isAlive()) {
                MCReflect.ggDrawString(graphics, this.font, "*", x + w - 10, 8, 0xFF6666, false);
            }
            x += w + 4;
        }
    }

    private void renderStatusBar(GuiGraphics graphics, TerminalSession s) {
        String status = String.format(" %s  |  %dx%d  |  %s  |  %s",
                s.getName(), cols, rows,
                s.isAlive() ? "running" : "exited",
                System.getProperty("os.name"));
        MCReflect.ggDrawString(graphics, this.font, status, 6,
            this.height - STATUS_BAR_HEIGHT + 5, 0xC0C0C0, false);

        String warn = "[!] Full OS shell access";
        int w = MCReflect.fontWidth(this.font, warn);
        MCReflect.ggDrawString(graphics, this.font, warn, this.width - w - 6,
            this.height - STATUS_BAR_HEIGHT + 5, 0xFFAA44, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlShift = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrlShift && keyCode == GLFW.GLFW_KEY_Q) {
            forceClose();
            return true;
        }
        if (ctrlShift && keyCode == GLFW.GLFW_KEY_T) {
            TerminalSession s = TerminalSessionManager.getInstance().createSession(cols, rows);
            this.renderer = new TerminalRenderer(s);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < TAB_BAR_HEIGHT) {
            int x = 8;
            List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
            for (int i = 0; i < sessions.size(); i++) {
                TerminalSession s = sessions.get(i);
                String label = s.getName();
                int w = MCReflect.fontWidth(this.font, label) + 16;
                if (mouseX >= x && mouseX < x + w) {
                    TerminalSessionManager.getInstance().setActiveIndex(i);
                    this.renderer = new TerminalRenderer(s);
                    return true;
                }
                x += w + 4;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        terminalFocused = true;
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

    private void forceClose() {
        TerminalSessionManager.getInstance().closeAll();
        this.onClose();
    }

    @Override
    public void onClose() {
        if (MineTerminalConfig.CLIENT.confirmCloseOnProcessExit.get()) {
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
