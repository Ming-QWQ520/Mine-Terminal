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
 * 标准 Forge 开发流程：直接使用 Component.literal, Minecraft.getInstance() 等。
 */
public class TerminalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("terminal")
                        .executes(ctx -> {
                            if (Minecraft.getInstance().player == null) {
                                ctx.getSource().sendFailure(
                                    Component.literal("Terminal can only be opened client-side."));
                                return 0;
                            }
                            Minecraft.getInstance().execute(() ->
                                ClientTerminalManager.getInstance().openTerminal());
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
                                    com.mineterm.client.terminal.TerminalSessionManager.getInstance().closeAll();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("All terminal sessions terminated."),
                                            false);
                                    return 1;
                                }))
        );
    }
}
