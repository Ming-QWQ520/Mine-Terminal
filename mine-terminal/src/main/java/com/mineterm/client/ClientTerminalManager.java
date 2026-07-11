package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端管理器（单例）。
 *
 * 职责：
 *   - 监听键盘输入事件（InputEvent.Key），检测 Y 键按下
 *   - 打开/关闭终端 Screen
 *   - 在 Minecraft 关闭时清理所有 PTY
 *
 * 关键实现说明：
 *   不依赖 KeyMapping.consumeClick()，因为该方法是 MC 类的方法，
 *   在 production 环境（用户启动的真实 MC）中方法名会被 SRG 混淆
 *   (consumeClick → m_90837_)，直接调用会引发 NoSuchMethodError。
 *   改为监听 InputEvent.Key（Forge 事件，名称稳定）+ 自己跟踪按键状态。
 *
 * 通过 @Mod.EventBusSubscriber 自动注册到 FORGE 事件总线。
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    // 跟踪 Y 键状态，实现"按下触发"（不是持续按下）
    private static boolean yKeyWasDown = false;
    // 标记：本 tick 内是否需要打开终端（由 InputEvent.Key 设置，由 ClientTickEvent 消费）
    private static volatile boolean pendingToggle = false;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. Keybinding bound: {}",
            KeyBindings.OPEN_TERMINAL != null ? "yes" : "NO (NULL!)");
        // 注册 shutdown hook，确保关闭时清理 PTY
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                TerminalSessionManager.getInstance().closeAll();
            } catch (Throwable t) {
                // ignore
            }
        }, "mine-terminal-shutdown"));
    }

    /**
     * 监听键盘输入事件。
     * InputEvent.Key 是 Forge 事件，方法名在 production 环境稳定。
     * 我们在这里检测 Y 键的"按下"动作（action == PRESS）。
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key e) {
        if (!INSTANCE.initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        // 如果当前在某个 Screen（如聊天框、菜单），不处理（避免冲突）
        if (mc.screen != null) {
            // 但如果是 TerminalScreen 自己，让 Screen 处理；这里跳过
            return;
        }

        // 检查是否是 Y 键按下
        // GLFW_PRESS = 1, GLFW_RELEASE = 0, GLFW_REPEAT = 2
        if (e.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_Y && e.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            pendingToggle = true;
            MineTerminal.LOGGER.info("[Mine-Terminal] Y key pressed (InputEvent.Key), pending toggle set.");
        }
    }

    /**
     * 在 client tick 中消费 pendingToggle 标志，打开/关闭终端。
     * 这样保证 Screen 的创建在主线程的 tick 阶段，避免渲染冲突。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!INSTANCE.initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (pendingToggle) {
            pendingToggle = false;
            MineTerminal.LOGGER.info("[Mine-Terminal] Consuming pending toggle, opening terminal screen.");
            INSTANCE.toggleTerminal();
        }
    }

    /**
     * 切换终端界面：如果未打开则打开；如果已打开则关闭。
     */
    public void toggleTerminal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TerminalScreen) {
            mc.setScreen(null);
        } else {
            try {
                mc.setScreen(new TerminalScreen());
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
                mc.setScreen(new TerminalScreen());
            } catch (Throwable t) {
                MineTerminal.LOGGER.error("[Mine-Terminal] Failed to open TerminalScreen via command", t);
            }
        }
    }
}
