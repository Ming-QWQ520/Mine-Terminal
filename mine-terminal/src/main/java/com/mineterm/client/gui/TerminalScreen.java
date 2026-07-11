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
 * ★ 关键设计：所有 @Override 方法名必须用 SRG 名（m_xxxxx_），不能用 deobf 名。
 *
 * 原因：我们绕过了 ForgeGradle 的 reobf 步骤，所以编译后的字节码方法名
 * 不会被自动映射为 SRG 名。如果用 deobf 名（如 render、keyPressed、isPauseScreen），
 * MC 在 production 环境按 SRG 名查找方法时找不到我们的重写，会调用父类默认实现。
 * 这会导致 Screen 空白、不响应输入、isPauseScreen 返回 true 等问题。
 *
 * 解决方案：方法名直接用 SRG 名（如 m_88315_、m_7933_、m_7043_），
 * Java 允许方法名包含下划线和数字。加注释说明对应的 deobf 名。
 *
 * SRG 名映射表（来自 Forge 1.20.1 MCPConfig joined.tsrg）：
 *   init()                          → m_7856_
 *   render(GuiGraphics, int, int, float) → m_88315_
 *   keyPressed(int, int, int)       → m_7933_
 *   charTyped(char, int)            → m_5534_
 *   mouseClicked(double, double, int) → m_6375_
 *   mouseScrolled(double, double, double) → m_6050_
 *   onClose()                       → m_7379_
 *   isPauseScreen()                 → m_7043_
 *   resize(Minecraft, int, int)     → m_6574_
 *
 * 方法体内的所有 MC 类方法调用通过 {@link MCReflect} 反射，避免 SRG 混淆。
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
        super((Component) MCReflect.componentTranslatable("gui.mineterm.title"));
    }

    /** init() — SRG: m_7856_ */
    @Override
    protected void m_7856_() {
        initInternal();
    }

    /** dev 环境兼容：init() */
    @Override
    protected void init() {
        initInternal();
    }

    private void initInternal() {
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

    /** resize(Minecraft, int, int) — SRG: m_6574_ */
    @Override
    public void m_6574_(net.minecraft.client.Minecraft mc, int w, int h) {
        resizeInternal(mc, w, h);
    }

    /** dev 环境兼容：resize */
    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        resizeInternal(mc, w, h);
    }

    private void resizeInternal(net.minecraft.client.Minecraft mc, int w, int h) {
        // 调用父类 resize（用反射避免 SRG 问题）
        // 实际上 super.resize 在 production 也会被映射，但我们用 SRG 名方法
        // 直接手动设置字段
        this.width = w;
        this.height = h;
        initInternal();
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

    /** render(GuiGraphics, int, int, float) — SRG: m_88315_ */
    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInternal(graphics, mouseX, mouseY, partialTick);
    }

    /** dev 环境兼容：render */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInternal(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInternal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MCReflect.ggFill(graphics, 0, 0, this.width, this.height, 0x80000000);
        MCReflect.ggFill(graphics, 0, 0, this.width, TAB_BAR_HEIGHT, 0x80000000);
        MCReflect.ggFill(graphics, 0, this.height - STATUS_BAR_HEIGHT, this.width, this.height, 0x80000000);

        renderTabs(graphics, mouseX, mouseY);

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

    /** keyPressed(int, int, int) — SRG: m_7933_ */
    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        return keyPressedInternal(keyCode, scanCode, modifiers);
    }

    /** dev 环境兼容：keyPressed */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedInternal(keyCode, scanCode, modifiers);
    }

    private boolean keyPressedInternal(int keyCode, int scanCode, int modifiers) {
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
        return false;  // 不调用 super.keyPressed 避免 SRG 问题
    }

    /** charTyped(char, int) — SRG: m_5534_ */
    @Override
    public boolean m_5534_(char c, int modifiers) {
        return charTypedInternal(c, modifiers);
    }

    /** dev 环境兼容：charTyped */
    @Override
    public boolean charTyped(char c, int modifiers) {
        return charTypedInternal(c, modifiers);
    }

    private boolean charTypedInternal(char c, int modifiers) {
        TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
        if (active != null) {
            boolean handled = TerminalKeyAdapter.handleCharTyped(active, c, modifiers);
            if (handled) return true;
        }
        return false;
    }

    /** mouseClicked(double, double, int) — SRG: m_6375_ */
    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        return mouseClickedInternal(mouseX, mouseY, button);
    }

    /** dev 环境兼容：mouseClicked */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedInternal(mouseX, mouseY, button);
    }

    private boolean mouseClickedInternal(double mouseX, double mouseY, int button) {
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
            return false;
        }

        terminalFocused = true;
        return true;
    }

    /** mouseScrolled(double, double, double) — SRG: m_6050_ */
    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        return mouseScrolledInternal(mouseX, mouseY, delta);
    }

    /** dev 环境兼容：mouseScrolled */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseScrolledInternal(mouseX, mouseY, delta);
    }

    private boolean mouseScrolledInternal(double mouseX, double mouseY, double delta) {
        if (renderer != null) {
            if (delta > 0) renderer.scrollUp((int) Math.ceil(delta));
            else if (delta < 0) renderer.scrollDown((int) -Math.floor(delta));
        }
        return true;
    }

    /** onClose() — SRG: m_7379_ */
    @Override
    public void m_7379_() {
        onCloseInternal();
    }

    /** dev 环境兼容：onClose */
    @Override
    public void onClose() {
        onCloseInternal();
    }

    private void onCloseInternal() {
        if (MineTerminalConfig.CLIENT.confirmCloseOnProcessExit.get()) {
            for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                if (!s.isAlive()) TerminalSessionManager.getInstance().closeSession(s);
            }
        }
        // 用反射调用 Minecraft.setScreen(null)
        MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
        if (mc != null) mc.setScreen(null);
    }

    private void forceClose() {
        TerminalSessionManager.getInstance().closeAll();
        MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
        if (mc != null) mc.setScreen(null);
    }

    /** isPauseScreen() — SRG: m_7043_ */
    @Override
    public boolean m_7043_() {
        return false;
    }

    /** dev 环境兼容：isPauseScreen */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
