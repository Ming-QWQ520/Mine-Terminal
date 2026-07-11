package com.mineterm.command;

import com.mineterm.MineTerminal;
import com.mineterm.client.ClientTerminalManager;
import com.mineterm.client.util.MCReflect;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /terminal 命令：在客户端打开终端界面。
 *
 * 所有 MC 类方法调用通过 MCReflect 反射，避免 SRG 混淆。
 * Commands.literal 是 Forge 提供的（在 fmlcore 中），方法名稳定。
 */
public class TerminalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("terminal")
                        .executes(ctx -> {
                            MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
                            if (mc == null || mc.getPlayer() == null) {
                                MCReflect.cssSendFailure(ctx.getSource(),
                                    (Component) MCReflect.componentLiteral("Terminal can only be opened client-side."));
                                return 0;
                            }
                            mc.execute(() -> ClientTerminalManager.getInstance().openTerminal());
                            MineTerminal.LOGGER.info("[Mine-Terminal] /terminal command issued.");
                            return 1;
                        })
                        .then(Commands.literal("close")
                                .executes(ctx -> {
                                    MCReflect.MinecraftHolder mc = MCReflect.getMinecraft();
                                    if (mc != null) {
                                        mc.execute(() -> {
                                            Object screen = mc.getScreen();
                                            if (screen != null) {
                                                MCReflect.screenOnClose(screen);
                                            }
                                        });
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("killall")
                                .executes(ctx -> {
                                    com.mineterm.client.terminal.TerminalSessionManager.getInstance().closeAll();
                                    MCReflect.cssSendSuccess(ctx.getSource(),
                                        () -> (Component) MCReflect.componentLiteral("All terminal sessions terminated."),
                                        false);
                                    return 1;
                                }))
        );
    }
}
