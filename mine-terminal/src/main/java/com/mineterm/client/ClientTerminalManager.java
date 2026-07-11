package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 客户端管理器（单例）。
 *
 * 关键实现说明：
 *   - Minecraft.getInstance() 在 production 被 SRG 混淆为 m_91087_()
 *   - Minecraft.player 字段在 production 被 SRG 混淆为 f_91072_
 *   - Minecraft.screen 字段在 production 被 SRG 混淆为 f_91062_
 *   - Minecraft.setScreen() 在 production 被 SRG 混淆为 m_91152_
 *
 * 所有 MC 类访问都用反射 + SRG 名称，避免 NoSuchMethodError / NoSuchFieldError。
 *
 * 按键检测：GLFW.glfwGetKey() 是 LWJGL 静态方法，不受 SRG 影响。
 */
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private boolean yKeyWasDown = false;

    // 反射缓存：Minecraft 类的 SRG 方法/字段
    private Method getInstanceMethod = null;
    private Method setScreenMethod = null;
    private Field playerField = null;
    private Field screenField = null;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. (GLFW poll + reflection mode)");

        // 反射查找 Minecraft.getInstance() — SRG 名 m_91087_
        try {
            getInstanceMethod = ObfuscationReflectionHelper.findMethod(Minecraft.class, "m_91087_");
            MineTerminal.LOGGER.info("[Mine-Terminal] Minecraft.getInstance() resolved: {}", getInstanceMethod);
        } catch (Throwable t) {
            try {
                getInstanceMethod = Minecraft.class.getMethod("getInstance");
                MineTerminal.LOGGER.info("[Mine-Terminal] getInstance resolved (deobf fallback): {}", getInstanceMethod);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve getInstance", t2);
            }
        }

        // 反射查找 Minecraft.setScreen(Screen) — SRG 名 m_91152_
        try {
            setScreenMethod = ObfuscationReflectionHelper.findMethod(
                Minecraft.class, "m_91152_", Screen.class);
            MineTerminal.LOGGER.info("[Mine-Terminal] setScreen resolved: {}", setScreenMethod);
        } catch (Throwable t) {
            try {
                setScreenMethod = Minecraft.class.getMethod("setScreen", Screen.class);
                MineTerminal.LOGGER.info("[Mine-Terminal] setScreen resolved (deobf fallback): {}", setScreenMethod);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve setScreen", t2);
            }
        }

        // 反射查找 Minecraft.player 字段 — SRG 名 f_91072_
        try {
            playerField = ObfuscationReflectionHelper.findField(Minecraft.class, "f_91072_");
            MineTerminal.LOGGER.info("[Mine-Terminal] Minecraft.player field resolved: {}", playerField);
        } catch (Throwable t) {
            try {
                playerField = Minecraft.class.getField("player");
                MineTerminal.LOGGER.info("[Mine-Terminal] player field resolved (deobf fallback): {}", playerField);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve player field", t2);
            }
        }

        // 反射查找 Minecraft.screen 字段 — SRG 名 f_91062_
        try {
            screenField = ObfuscationReflectionHelper.findField(Minecraft.class, "f_91062_");
            MineTerminal.LOGGER.info("[Mine-Terminal] Minecraft.screen field resolved: {}", screenField);
        } catch (Throwable t) {
            try {
                screenField = Minecraft.class.getField("screen");
                MineTerminal.LOGGER.info("[Mine-Terminal] screen field resolved (deobf fallback): {}", screenField);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve screen field", t2);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { TerminalSessionManager.getInstance().closeAll(); } catch (Throwable t) {}
        }, "mine-terminal-shutdown"));
    }

    /**
     * 由主类通过 addListener 注册到 FORGE 事件总线。
     */
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!initialized) return;

        // 反射获取 Minecraft 实例
        Minecraft mc = getMinecraftInstance();
        if (mc == null) return;

        // 反射获取 player 字段，判断玩家是否在游戏中
        Object player = getFieldValue(mc, playerField);
        if (player == null) return;

        // 反射获取 screen 字段，如果有 Screen 打开则不处理
        Object currentScreen = getFieldValue(mc, screenField);
        if (currentScreen != null) {
            yKeyWasDown = false;
            return;
        }

        // 直接通过 GLFW 查询 Y 键状态（LWJGL 静态方法，不受 SRG 影响）
        long window = GLFW.glfwGetCurrentContext();
        boolean yKeyDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;

        // 边沿检测：从"松开"→"按下"才触发
        if (yKeyDown && !yKeyWasDown) {
            MineTerminal.LOGGER.info("[Mine-Terminal] Y key pressed (GLFW poll), opening terminal screen.");
            toggleTerminal(mc);
        }
        yKeyWasDown = yKeyDown;
    }

    public void toggleTerminal() {
        toggleTerminal(getMinecraftInstance());
    }

    private void toggleTerminal(Minecraft mc) {
        if (mc == null) return;
        Object currentScreen = getFieldValue(mc, screenField);
        if (currentScreen instanceof TerminalScreen) {
            setScreenSafe(mc, null);
        } else {
            try {
                setScreenSafe(mc, new TerminalScreen());
                MineTerminal.LOGGER.info("[Mine-Terminal] TerminalScreen opened successfully.");
            } catch (Throwable t) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to open TerminalScreen", t);
            }
        }
    }

    public void openTerminal() {
        Minecraft mc = getMinecraftInstance();
        if (mc == null) return;
        Object currentScreen = getFieldValue(mc, screenField);
        if (!(currentScreen instanceof TerminalScreen)) {
            try {
                setScreenSafe(mc, new TerminalScreen());
            } catch (Throwable t) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to open TerminalScreen via command", t);
            }
        }
    }

    private Minecraft getMinecraftInstance() {
        if (getInstanceMethod == null) return null;
        try {
            return (Minecraft) getInstanceMethod.invoke(null);
        } catch (Throwable t) {
            MineTerminal.LOGGER.warn("[Mine-Terminal] getInstance invoke failed: {}", t.getMessage());
            return null;
        }
    }

    private static Object getFieldValue(Object obj, Field field) {
        if (field == null || obj == null) return null;
        try {
            return field.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private void setScreenSafe(Minecraft mc, Screen screen) {
        if (setScreenMethod == null) {
            MineTerminal.LOGGER.error("[Mine-Terminal] setScreen method not resolved, cannot open screen.");
            return;
        }
        try {
            setScreenMethod.invoke(mc, screen);
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] setScreen reflect invoke failed", t);
        }
    }
}
