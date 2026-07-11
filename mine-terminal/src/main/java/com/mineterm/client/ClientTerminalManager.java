package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * 客户端管理器（单例）。
 *
 * 不使用 @Mod.EventBusSubscriber，而是由主类在构造时通过 addListener 显式注册。
 * 这样避免 Forge 自动订阅机制对 static 方法的处理差异。
 *
 * 按键检测：在 ClientTickEvent 中直接轮询 GLFW 键盘状态。
 *  - GLFW.glfwGetCurrentContext() / glfwGetKey() 是 LWJGL 静态方法，不受 SRG 混淆影响
 *  - TickEvent.ClientTickEvent 是 Forge 事件，每 tick 都触发
 *  - setScreen 通过 ObfuscationReflectionHelper 反射调用，避免 SRG 方法名问题
 */
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private boolean yKeyWasDown = false;
    private Method setScreenMethod = null;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. (GLFW poll mode)");

        // 预先查找 setScreen 方法（SRG 名称 m_91152_）
        try {
            setScreenMethod = ObfuscationReflectionHelper.findMethod(
                Minecraft.class, "m_91152_", Screen.class);
            MineTerminal.LOGGER.info("[Mine-Terminal] setScreen method resolved: {}", setScreenMethod);
        } catch (Throwable t) {
            try {
                setScreenMethod = Minecraft.class.getMethod("setScreen", Screen.class);
                MineTerminal.LOGGER.info("[Mine-Terminal] setScreen method resolved (deobf): {}", setScreenMethod);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve setScreen method", t2);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { TerminalSessionManager.getInstance().closeAll(); } catch (Throwable t) {}
        }, "mine-terminal-shutdown"));
    }

    /**
     * 由主类通过 addListener 注册到 FORGE 事件总线。
     * 非静态方法，实例方法，避免 Forge 对 static 方法的处理差异。
     */
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // 如果当前已打开 Screen，不处理
        if (mc.screen != null) {
            yKeyWasDown = false;
            return;
        }

        // 直接通过 GLFW 查询 Y 键状态
        long window = GLFW.glfwGetCurrentContext();
        boolean yKeyDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;

        // 边沿检测：从"松开"→"按下"才触发
        if (yKeyDown && !yKeyWasDown) {
            MineTerminal.LOGGER.info("[Mine-Terminal] Y key pressed (GLFW poll), opening terminal screen.");
            toggleTerminal();
        }
        yKeyWasDown = yKeyDown;
    }

    public void toggleTerminal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TerminalScreen) {
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
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TerminalScreen)) {
            try {
                setScreenSafe(mc, new TerminalScreen());
            } catch (Throwable t) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to open TerminalScreen via command", t);
            }
        }
    }

    private void setScreenSafe(Minecraft mc, Screen screen) {
        if (setScreenMethod != null) {
            try {
                setScreenMethod.invoke(mc, screen);
                return;
            } catch (Throwable t) {
                MineTerminal.LOGGER.warn("[Mine-Terminal] setScreen reflect invoke failed: {}", t.getMessage());
            }
        }
        try {
            mc.setScreen(screen);
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] setScreen direct call failed", t);
        }
    }
}
