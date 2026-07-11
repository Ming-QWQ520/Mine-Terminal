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
 * ★★ 彻底防崩溃设计 ★★
 *
 * 1. 所有 @Override 方法同时提供 SRG 名 (m_xxxxx_) 和 deobf 名两个版本
 *    — 防止 MC 在 production 找不到方法重写
 *
 * 2. 所有 MC 类字段访问 (width, height, font, minecraft) 通过 MCReflect 反射
 *    — 防止 NoSuchFieldError（字段名也被 SRG 混淆）
 *
 * 3. 所有 MC 类方法调用 (Component.translatable, GuiGraphics.fill, Font.width) 通过 MCReflect 反射
 *    — 防止 NoSuchMethodError
 *
 * 4. 所有 internal 方法用 try-catch 包裹，任何异常都静默处理，绝不抛出
 *    — 防止单次渲染失败导致整个游戏崩溃
 *
 * 5. 渲染前检查所有依赖（renderer, session, font）是否非 null
 *    — 防止 NPE
 *
 * SRG 名映射表（来自 Forge 1.20.1 MCPConfig joined.tsrg）：
 *   Screen.init()                          → m_7856_
 *   Screen.render(GuiGraphics,int,int,float) → m_88315_
 *   Screen.keyPressed(int,int,int)          → m_7933_
 *   Screen.charTyped(char,int)              → m_5534_
 *   Screen.mouseClicked(double,double,int)  → m_6375_
 *   Screen.mouseScrolled(double,double,double) → m_6050_
 *   Screen.onClose()                        → m_7379_
 *   Screen.isPauseScreen()                  → m_7043_
 *   Screen.resize(Minecraft,int,int)        → m_6574_
 *   Screen.width                            → f_96543_
 *   Screen.height                           → f_96544_
 *   Screen.font                             → f_96547_
 *   Screen.minecraft                        → f_96541_
 */
public class TerminalScreen extends Screen {

    private static final int TAB_BAR_HEIGHT = 22;
    private static final int STATUS_BAR_HEIGHT = 18;
    private static final int MARGIN = 6;

    private int cellWidth = 7;
    private int cellHeight = 12;
    private int terminalX, terminalY, terminalW, terminalH;
    private int cols, rows;
    private int cachedWidth = 0;
    private int cachedHeight = 0;
    private Object cachedFont = null;

    private TerminalRenderer renderer;
    private boolean terminalFocused = true;

    public TerminalScreen() {
        super((Component) MCReflect.componentTranslatable("gui.mineterm.title"));
    }

    /** 获取 width（反射访问，避免 NoSuchFieldError） */
    private int getWidthSafe() {
        try {
            cachedWidth = MCReflect.screenGetWidth(this);
            if (cachedWidth <= 0) cachedWidth = 854;  // 默认值
        } catch (Throwable t) {
            cachedWidth = 854;
        }
        return cachedWidth;
    }

    /** 获取 height（反射访问） */
    private int getHeightSafe() {
        try {
            cachedHeight = MCReflect.screenGetHeight(this);
            if (cachedHeight <= 0) cachedHeight = 480;
        } catch (Throwable t) {
            cachedHeight = 480;
        }
        return cachedHeight;
    }

    /** 获取 font（反射访问） */
    private Object getFontSafe() {
        try {
            if (cachedFont == null) {
                cachedFont = MCReflect.screenGetFont(this);
            }
        } catch (Throwable t) {
            cachedFont = null;
        }
        return cachedFont;
    }

    /** init() — SRG: m_7856_ */
    @Override
    protected void m_7856_() { initInternal(); }

    @Override
    protected void init() { initInternal(); }

    private void initInternal() {
        try {
            int fontSize = MineTerminalConfig.CLIENT.fontSize.get();
            int lineHeight = MineTerminalConfig.CLIENT.lineHeight.get();
            this.cellWidth = Math.max(5, fontSize * 5 / 9);
            this.cellHeight = lineHeight;

            int w = getWidthSafe();
            int h = getHeightSafe();
            this.terminalX = MARGIN;
            this.terminalY = TAB_BAR_HEIGHT + MARGIN;
            this.terminalW = w - MARGIN * 2;
            this.terminalH = h - TAB_BAR_HEIGHT - STATUS_BAR_HEIGHT - MARGIN * 2;
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
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] initInternal failed", t);
        }
    }

    /** resize(Minecraft,int,int) — SRG: m_6574_ */
    @Override
    public void m_6574_(net.minecraft.client.Minecraft mc, int w, int h) {
        resizeInternal(mc, w, h);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        resizeInternal(mc, w, h);
    }

    private void resizeInternal(net.minecraft.client.Minecraft mc, int w, int h) {
        try {
            cachedWidth = w;
            cachedHeight = h;
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
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] resizeInternal failed", t);
        }
    }

    /** render — SRG: m_88315_ */
    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInternal(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderInternal(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInternal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            int w = getWidthSafe();
            int h = getHeightSafe();
            Object font = getFontSafe();

            // 1. 绘制背景
            MCReflect.ggFill(graphics, 0, 0, w, h, 0x80000000);
            MCReflect.ggFill(graphics, 0, 0, w, TAB_BAR_HEIGHT, 0x80000000);
            MCReflect.ggFill(graphics, 0, h - STATUS_BAR_HEIGHT, w, h, 0x80000000);

            // 2. 渲染标签栏
            renderTabs(graphics, font);

            // 3. 渲染终端
            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null && this.renderer != null) {
                if (this.renderer.session != active) {
                    this.renderer = new TerminalRenderer(active);
                }
                this.renderer.render(graphics, terminalX, terminalY, cellWidth, cellHeight, cols, rows);
                renderStatusBar(graphics, font, active);
            } else if (font != null) {
                MCReflect.ggDrawCenteredString(graphics, font,
                    "No terminal session. Click '+' to create one.",
                    w / 2, h / 2, 0xFF6666);
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] renderInternal failed", t);
            // 不重新抛出，避免游戏崩溃
        }
    }

    private void renderTabs(GuiGraphics graphics, Object font) {
        try {
            if (font == null) return;
            List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
            int activeIdx = TerminalSessionManager.getInstance().getActiveIndex();
            int x = 8;
            for (int i = 0; i < sessions.size(); i++) {
                TerminalSession s = sessions.get(i);
                String label = s.getName();
                if (!s.isAlive()) label = "[" + label + ": exited]";
                int labelW = MCReflect.fontWidth(font, label) + 16;
                boolean activeTab = (i == activeIdx);
                int bg = activeTab ? 0xFF333333 : 0xFF1A1A1A;
                int fg = activeTab ? 0xFFFFFF : 0xAAAAAA;
                MCReflect.ggFill(graphics, x, 3, x + labelW, TAB_BAR_HEIGHT - 1, bg);
                MCReflect.ggDrawString(graphics, font, label, x + 8, 8, fg, false);
                if (!s.isAlive()) {
                    MCReflect.ggDrawString(graphics, font, "*", x + labelW - 10, 8, 0xFF6666, false);
                }
                x += labelW + 4;
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    private void renderStatusBar(GuiGraphics graphics, Object font, TerminalSession s) {
        try {
            if (font == null) return;
            int h = getHeightSafe();
            int w = getWidthSafe();
            String status = String.format(" %s  |  %dx%d  |  %s  |  %s",
                    s.getName(), cols, rows,
                    s.isAlive() ? "running" : "exited",
                    System.getProperty("os.name"));
            MCReflect.ggDrawString(graphics, font, status, 6,
                h - STATUS_BAR_HEIGHT + 5, 0xC0C0C0, false);

            String warn = "[!] Full OS shell access";
            int warnW = MCReflect.fontWidth(font, warn);
            MCReflect.ggDrawString(graphics, font, warn, w - warnW - 6,
                h - STATUS_BAR_HEIGHT + 5, 0xFFAA44, false);
        } catch (Throwable t) {
            // 静默
        }
    }

    /** keyPressed — SRG: m_7933_ */
    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        return keyPressedInternal(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedInternal(keyCode, scanCode, modifiers);
    }

    private boolean keyPressedInternal(int keyCode, int scanCode, int modifiers) {
        try {
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
                onCloseInternal();
                return true;
            }

            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null) {
                boolean handled = TerminalKeyAdapter.handleKeyPressed(active, keyCode, scanCode, modifiers);
                if (handled) return true;
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] keyPressedInternal failed", t);
        }
        return false;
    }

    /** charTyped — SRG: m_5534_ */
    @Override
    public boolean m_5534_(char c, int modifiers) {
        return charTypedInternal(c, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return charTypedInternal(c, modifiers);
    }

    private boolean charTypedInternal(char c, int modifiers) {
        try {
            TerminalSession active = TerminalSessionManager.getInstance().getActiveSession();
            if (active != null) {
                boolean handled = TerminalKeyAdapter.handleCharTyped(active, c, modifiers);
                if (handled) return true;
            }
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] charTypedInternal failed", t);
        }
        return false;
    }

    /** mouseClicked — SRG: m_6375_ */
    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        return mouseClickedInternal(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedInternal(mouseX, mouseY, button);
    }

    private boolean mouseClickedInternal(double mouseX, double mouseY, int button) {
        try {
            Object font = getFontSafe();
            if (mouseY < TAB_BAR_HEIGHT) {
                int x = 8;
                List<TerminalSession> sessions = TerminalSessionManager.getInstance().getSessions();
                for (int i = 0; i < sessions.size(); i++) {
                    TerminalSession s = sessions.get(i);
                    String label = s.getName();
                    int labelW = MCReflect.fontWidth(font, label) + 16;
                    if (mouseX >= x && mouseX < x + labelW) {
                        TerminalSessionManager.getInstance().setActiveIndex(i);
                        this.renderer = new TerminalRenderer(s);
                        return true;
                    }
                    x += labelW + 4;
                }
                return false;
            }

            terminalFocused = true;
            return true;
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] mouseClickedInternal failed", t);
            return false;
        }
    }

    /** mouseScrolled — SRG: m_6050_ */
    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        return mouseScrolledInternal(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseScrolledInternal(mouseX, mouseY, delta);
    }

    private boolean mouseScrolledInternal(double mouseX, double mouseY, double delta) {
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

    /** onClose — SRG: m_7379_ */
    @Override
    public void m_7379_() { onCloseInternal(); }

    @Override
    public void onClose() { onCloseInternal(); }

    private void onCloseInternal() {
        try {
            if (MineTerminalConfig.CLIENT.confirmCloseOnProcessExit.get()) {
                for (TerminalSession s : TerminalSessionManager.getInstance().getSessions()) {
                    if (!s.isAlive()) TerminalSessionManager.getInstance().closeSession(s);
                }
            }
            MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
            if (mc != null) mc.setScreen(null);
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] onCloseInternal failed", t);
        }
    }

    private void forceClose() {
        try {
            TerminalSessionManager.getInstance().closeAll();
            MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
            if (mc != null) mc.setScreen(null);
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] forceClose failed", t);
        }
    }

    /** isPauseScreen — SRG: m_7043_ */
    @Override
    public boolean m_7043_() { return false; }

    @Override
    public boolean isPauseScreen() { return false; }
}
