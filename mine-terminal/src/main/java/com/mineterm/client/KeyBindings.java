package com.mineterm.client;

import com.mineterm.MineTerminal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键绑定。
 *
 * 默认快捷键（可在控制设置中修改）：
 *   - Ctrl+Shift+T  打开/关闭终端（打开键）
 *
 * 注意：Ctrl+Shift+T 同时也是新建标签页的快捷键——
 * 在终端 Screen 内部，Ctrl+Shift+T 创建新标签；
 * 在 Minecraft 任意界面，Ctrl+Shift+T 切换终端显示。
 * 这两种行为通过"当前是否在 TerminalScreen"区分。
 */
public class KeyBindings {

    public static final String CATEGORY = "key.categories.mineterm";

    public static KeyMapping OPEN_TERMINAL;

    public static void register() {
        // 兼容旧调用入口；真正注册通过 RegisterKeyMappingsEvent 完成
        // 详见 #register(RegisterKeyMappingsEvent)
    }

    /**
     * 标准做法：在 RegisterKeyMappingsEvent 中调用。
     */
    public static void register(net.minecraftforge.client.event.RegisterKeyMappingsEvent e) {
        OPEN_TERMINAL = new KeyMapping(
                "key.mineterm.open_terminal",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                CATEGORY
        );
        e.register(OPEN_TERMINAL);
        MineTerminal.LOGGER.info("[Mine-Terminal] Keybinding registered: open_terminal");
    }
}
