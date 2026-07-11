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
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private boolean openKeyPressed = false;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized.");
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

        // 检查打开快捷键
        if (KeyBindings.OPEN_TERMINAL.consumeClick()) {
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
