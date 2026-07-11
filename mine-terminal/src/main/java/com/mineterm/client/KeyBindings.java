package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键绑定。
 *
 * 默认快捷键：Y（可在控制设置中修改）
 *   - 在游戏中：打开/关闭终端
 *
 * 注册方式：通过 RegisterKeyMappingsEvent（MOD bus 事件）
 */
public class KeyBindings {

    public static final String CATEGORY = "key.categories.mineterm";

    // 用 static + 初始化块 + public，保证 ClassLoader 加载时即创建
    // 这样即使 RegisterKeyMappingsEvent 没触发，consumeClick() 也不会 NPE
    public static KeyMapping OPEN_TERMINAL = new KeyMapping(
            "key.mineterm.open_terminal",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,    // 默认 Y 键
            CATEGORY
    );

    /**
     * 标准做法：在 RegisterKeyMappingsEvent 中调用。
     * 这个方法由 MineTerminal 主类通过 modBus.addListener 注册。
     */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent e) {
        e.register(OPEN_TERMINAL);
        MineTerminal.LOGGER.info("[Mine-Terminal] Keybinding registered: open_terminal (Y key)");
    }

    /**
     * 兼容旧入口（保留以防主类调用错误）。
     */
    public static void register() {
        MineTerminal.LOGGER.warn("[Mine-Terminal] KeyBindings.register() called directly — should not happen");
    }
}
