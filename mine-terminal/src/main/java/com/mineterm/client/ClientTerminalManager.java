package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端管理器（单例）。
 *
 * 职责：
 *   - 监听客户端 tick，检查快捷键
 *   - 打开/关闭终端 Screen
 *   - 在 Minecraft 关闭时清理所有 PTY
 *
 * 通过 @Mod.EventBusSubscriber 自动注册到 FORGE 事件总线。
 * onClientTick 是 static 方法，被 Forge 反射调用。
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private static long tickCount = 0;
    private static boolean lastKeyState = false;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. Keybinding: {}", 
            KeyBindings.OPEN_TERMINAL == null ? "NULL!" : KeyBindings.OPEN_TERMINAL.getName());
        // 注册 shutdown hook，确保关闭时清理 PTY
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
        if (mc.player == null) return;

        tickCount++;

        // 检查打开快捷键
        if (KeyBindings.OPEN_TERMINAL == null) {
            // 不应该发生，但避免 NPE
            if (tickCount % 100 == 0) {
                MineTerminal.LOGGER.error("[Mine-Terminal] OPEN_TERMINAL keymapping is NULL!");
            }
            return;
        }

        // consumeClick() 返回 true 表示本 tick 内按键被按下过
        boolean pressed = KeyBindings.OPEN_TERMINAL.consumeClick();
        if (pressed) {
            MineTerminal.LOGGER.info("[Mine-Terminal] Open key pressed, toggling terminal screen.");
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
            mc.setScreen(new TerminalScreen());
        }
    }

    /**
     * 直接打开终端界面（由 /terminal 命令调用）。
     */
    public void openTerminal() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TerminalScreen)) {
            mc.setScreen(new TerminalScreen());
        }
    }
}
