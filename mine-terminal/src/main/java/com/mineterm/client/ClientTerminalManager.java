package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mineterm.client.gui.TerminalScreen;
import com.mineterm.client.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端管理器（单例）。
 *
 * 职责：
 *   - 在 client tick 中直接轮询 GLFW 键盘状态，检测 Y 键按下
 *   - 打开/关闭终端 Screen
 *   - 在 Minecraft 关闭时清理所有 PTY
 *
 * 标准 Forge 开发流程：源码用 deobf 名，ForgeGradle 的 reobf 任务自动映射为 SRG 名。
 */
@Mod.EventBusSubscriber(modid = MineTerminal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTerminalManager {

    private static final ClientTerminalManager INSTANCE = new ClientTerminalManager();

    private boolean initialized = false;
    private boolean yKeyWasDown = false;

    public static ClientTerminalManager getInstance() { return INSTANCE; }

    private ClientTerminalManager() {}

    public void initialize() {
        initialized = true;
        MineTerminal.LOGGER.info("[Mine-Terminal] ClientTerminalManager initialized. (GLFW poll mode)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { TerminalSessionManager.getInstance().closeAll(); } catch (Throwable t) {}
        }, "mine-terminal-shutdown"));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        INSTANCE.tick();
    }

    private void tick() {
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
