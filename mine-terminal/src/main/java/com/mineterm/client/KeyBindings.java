package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键绑定。
 *
 * 默认快捷键：Ctrl + T（可在 Minecraft 控制设置 → 按键绑定 中修改）
 *   - 在游戏中：打开/关闭终端
 *
 * 注册方式：通过 RegisterKeyMappingsEvent（MOD bus 事件）
 * 该按键绑定会出现在 Minecraft 的"选项 → 控制 → 按键绑定"中，
 * 玩家可以自由修改为任何组合键（包括 Ctrl+Alt+T）。
 */
public class KeyBindings {

    public static final String CATEGORY = "key.categories.mineterm";

    /**
     * 打开/关闭终端的按键绑定。
     * 默认：Ctrl + T
     * 玩家可在控制设置中修改为 Ctrl+Alt+T 或其他任意组合。
     */
    public static KeyMapping OPEN_TERMINAL = new KeyMapping(
            "key.mineterm.open_terminal",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            CATEGORY
    );

    /**
     * 在 RegisterKeyMappingsEvent 中注册按键绑定。
     * 由 MineTerminal 主类通过 modBus.addListener 调用。
     */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent e) {
        e.register(OPEN_TERMINAL);
        MineTerminal.LOGGER.info("[Mine-Terminal] Keybinding registered: open_terminal (default: Ctrl+T, configurable in controls)");
    }
}
