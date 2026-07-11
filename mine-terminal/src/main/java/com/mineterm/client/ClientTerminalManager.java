package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * 客户端管理器（单例）。
 *
 * 职责：
 *   - 在 client tick 中直接轮询 GLFW 键盘状态，检测 Y 键按下
 *   - 打开/关闭终端 Screen
 *   - 在 Minecraft 关闭时清理所有 PTY
 *
 * 关键实现说明（避免之前几次踩坑）：
 *
 * 1. 不用 KeyMapping.consumeClick()
 *    → 该方法是 MC 类方法，在 production 环境被 SRG 混淆为 m_90837_()，
 *      直接调用会引发 NoSuchMethodError。
 *
 * 2. 不用 InputEvent.Key
 *    → 该事件在玩家进入世界后（mc.screen == null 时）不会被 Forge 分发，
 *      Forge 在游戏中走的是 KeyMapping 系统。
 *
 * 3. 用 TickEvent.ClientTickEvent + GLFW.glfwGetKey()
 *    → ClientTickEvent 是 Forge 事件，方法名稳定。
 *    → glfwGetKey 是 LWJGL 静态方法，与 SRG 无关。
 *    → 用 glfwGetCurrentContext() 获取窗口句柄，避免调用 MC 类方法。
 *    → 用 ObfuscationReflectionHelper 反射调用 Minecraft.setScreen，
 *      避免 SRG 方法名问题。
 *
 * 通过 @Mod.EventBusSubscriber 自动注册到 FORGE 事件总线。
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private static boolean yKeyWasDown = false;
    private static Method setScreenMethod = null;

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
            // 回退：尝试用 deobf 名（dev 环境）
            try {
                setScreenMethod = Minecraft.class.getMethod("setScreen", Screen.class);
                MineTerminal.LOGGER.info("[Mine-Terminal] setScreen method resolved (deobf fallback): {}", setScreenMethod);
            } catch (Throwable t2) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to resolve setScreen method", t2);
            }
        }
        // 注册 shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                TerminalSessionManager.getInstance().closeAll();
            } catch (Throwable t) {
                // ignore
            }
        }, "mine-terminal-shutdown"));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!INSTANCE.initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // 如果当前已打开 Screen，不处理
        if (mc.screen != null) {
            yKeyWasDown = false;
            return;
        }

        // 直接通过 GLFW 查询 Y 键状态（避免调用 MC 类方法）
        long window = GLFW.glfwGetCurrentContext();
        boolean yKeyDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;

        // 边沿检测：从"松开"→"按下"才触发
        if (yKeyDown && !yKeyWasDown) {
            MineTerminal.LOGGER.info("[Mine-Terminal] Y key pressed (GLFW poll), opening terminal screen.");
            INSTANCE.toggleTerminal();
        }
        yKeyWasDown = yKeyDown;
    }

    /**
     * 切换终端界面：如果未打开则打开；如果已打开则关闭。
     * 用反射调用 Minecraft.setScreen 避免 SRG 混淆问题。
     */
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

    /**
     * 直接打开终端界面（由 /terminal 命令调用）。
     */
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

    /**
     * 用反射调用 Minecraft.setScreen(Screen)。
     * 优先使用 SRG 名称 m_91152_（production 环境），失败则回退到 deobf 名（dev 环境）。
     */
    private static void setScreenSafe(Minecraft mc, Screen screen) {
        if (setScreenMethod != null) {
            try {
                setScreenMethod.invoke(mc, screen);
                return;
            } catch (Throwable t) {
                MineTerminal.LOGGER.warn("[Mine-Terminal] setScreen reflect invoke failed: {}", t.getMessage());
            }
        }
        // 最后回退：直接调用（dev 环境或 AT 已处理的情况）
        try {
            mc.setScreen(screen);
        } catch (Throwable t) {
            MineTerminal.LOGGER.error("[Mine-Terminal] setScreen direct call failed", t);
        }
    }
}

