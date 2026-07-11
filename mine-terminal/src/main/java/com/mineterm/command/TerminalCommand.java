package com.mineterm.command;

import com.mineterm.MineTerminal;
import com.mineterm.client.ClientTerminalManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /terminal 命令：在客户端打开终端界面。
 *
 * 仅客户端可执行；服务端执行会提示无权限（因为模组在服务端自禁用）。
 * 注册由 {@link com.mineterm.MineTerminal#onRegisterCommands} 调用本类的 register 方法。
 */
public class TerminalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("terminal")
                        .executes(ctx -> {
                            // 必须在客户端执行
                            if (Minecraft.getInstance().player == null) {
                                ctx.getSource().sendFailure(Component.literal("Terminal can only be opened client-side."));
                                return 0;
                            }
                            // 异步切换到客户端线程打开 Screen
                            Minecraft.getInstance().execute(() -> {
                                ClientTerminalManager.getInstance().openTerminal();
                            });
                            MineTerminal.LOGGER.info("[Mine-Terminal] /terminal command issued.");
                            return 1;
                        })
                        .then(Commands.literal("close")
                                .executes(ctx -> {
                                    Minecraft.getInstance().execute(() -> {
                                        if (Minecraft.getInstance().screen != null) {
                                            Minecraft.getInstance().screen.onClose();
                                        }
                                    });
                                    return 1;
                                }))
                        .then(Commands.literal("killall")
                                .executes(ctx -> {
                                    // 强制关闭所有 PTY
                                    com.mineterm.client.terminal.TerminalSessionManager.getInstance().closeAll();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("All terminal sessions terminated."), false);
                                    return 1;
                                }))
        );
    }
}
