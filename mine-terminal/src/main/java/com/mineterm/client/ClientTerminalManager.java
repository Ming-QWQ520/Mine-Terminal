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
 *   - 在 client tick 中检测快捷键按下（通过 KeyMapping.consumeClick()）
 *   - 打开/关闭终端 Screen
 *   - 在 Minecraft 关闭时清理所有 PTY
 *
 * 标准 Forge 开发流程：源码用 deobf 名，ForgeGradle 的 reobf 任务自动映射为 SRG 名。
 * KeyMapping.consumeClick() 会被 reobf 正确映射，无需反射或 GLFW 轮询。
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. (KeyMapping consumeClick mode)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { TerminalSessionManager.getInstance().closeAll(); } catch (Throwable t) {}
        }, "mine-terminal-shutdown"));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!INSTANCE.initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // 如果当前已打开 Screen，不处理（让 Screen 自己处理按键）
        if (mc.screen != null) return;

        // 通过 KeyMapping.consumeClick() 检测按键按下
        // consumeClick() 会自动检查修饰键（Ctrl/Alt/Shift），与按键绑定设置一致
        if (KeyBindings.OPEN_TERMINAL.consumeClick()) {
            MineTerminal.LOGGER.info("[Mine-Terminal] Open terminal key pressed, toggling terminal screen.");
            INSTANCE.toggleTerminal();
        }
    }

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
