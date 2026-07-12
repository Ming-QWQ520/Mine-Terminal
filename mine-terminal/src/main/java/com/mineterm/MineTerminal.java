package com.mineterm;

import com.mineterm.client.ClientTerminalManager;
import com.mineterm.client.KeyBindings;
import com.mineterm.command.TerminalCommand;
import com.mineterm.common.MineTerminalConfig;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mine-Terminal 主模组类。
 *
 * 提供游戏内完整系统终端（PTY + ANSI/XTerm 全功能）。
 * 客户端专用模组，在专用服务端加载时会自动禁用并打印警告。
 *
 * 安全声明：
 *   本模组赋予 Minecraft 进程完整的操作系统 shell 权限。
 *   切勿以 root / Administrator 身份运行 Minecraft。
 *   作者不对用户在终端中执行的任何命令负责。
 */
@Mod(MineTerminal.MOD_ID)
public class MineTerminal {

    public static final String MOD_ID = "mineterm";
    public static final String MOD_NAME = "Mine-Terminal";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    private static MineTerminal INSTANCE;

    public MineTerminal() {
        INSTANCE = this;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MineTerminalConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MineTerminalConfig.COMMON_SPEC);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(KeyBindings::onRegisterKeyMappings);

        // 注册自身到 FORGE 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 显式注册 ClientTerminalManager 的 onClientTick 到 FORGE 总线
        ClientTerminalManager mgr = ClientTerminalManager.getInstance();
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e) -> mgr.onClientTick(e));
        LOGGER.info("[Mine-Terminal] Registered ClientTerminalManager.onClientTick to FORGE event bus.");
    }

    public static MineTerminal getInstance() {
        return INSTANCE;
    }

    private void commonSetup(final FMLCommonSetupEvent e) {
        LOGGER.info("[Mine-Terminal] common setup: security checks & platform validation.");
        e.enqueueWork(() -> {
            LOGGER.warn("============================================================");
            LOGGER.warn(" Mine-Terminal grants Minecraft full OS-shell access.     ");
            LOGGER.warn(" Do NOT run Minecraft as root/Administrator with this mod. ");
            LOGGER.warn("============================================================");
        });
    }

    private void clientSetup(final FMLClientSetupEvent e) {
        LOGGER.info("[Mine-Terminal] client setup: registering keybindings, terminal manager.");
        e.enqueueWork(() -> {
            ClientTerminalManager.getInstance().initialize();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        if (e.getServer().isDedicatedServer()) {
            LOGGER.error("[Mine-Terminal] This mod is CLIENT-ONLY and will self-disable on dedicated servers.");
            LOGGER.error("[Mine-Terminal] Please remove it from your server mods folder.");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        TerminalCommand.register(e.getDispatcher());
    }
}
