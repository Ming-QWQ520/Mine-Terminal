#!/usr/bin/env python3
"""
为 Minecraft 1.20.1 + Forge 47.4.20 生成编译用 stub jar。

策略：
  扫描 mine-terminal 源码，提取所有引用的 MC/Forge 类（import 语句），
  为每个类生成一个空实现的 .java 文件，只包含源码中实际调用的方法签名。

为了简化，我们手工列出 mine-terminal 用到的所有 MC/Forge 类型，
然后为它们生成最小 stub。

输出：/home/z/tools/libs/mc-stubs.jar
"""
import os
import subprocess
import sys
from pathlib import Path

JDK = Path.home() / "tools" / "jdk17" / "bin"
JAVAC = JDK / "javac"
JAR = JDK / "jar"

# mine-terminal 源码中引用的所有 MC/Forge 类（按 package 分组）
# 每个类只列出 mine-terminal 实际使用的方法/字段
STUBS = {
    # ============ Minecraft (net.minecraft.*) ============
    "net/minecraft/client/Minecraft.java": '''
package net.minecraft.client;
public class Minecraft {
    public static Minecraft getInstance() { return null; }
    public net.minecraft.client.gui.Font font;
    public net.minecraft.client.player.LocalPlayer player;
    public net.minecraft.client.gui.screens.Screen screen;
    public net.minecraft.client.KeyboardHandler keyboardHandler;
    public void setScreen(net.minecraft.client.gui.screens.Screen s) {}
    public void execute(Runnable r) { r.run(); }
    public boolean isLocalServer() { return false; }
}
''',

    "net/minecraft/client/KeyboardHandler.java": '''
package net.minecraft.client;
public class KeyboardHandler {
    public void setClipboard(String s) {}
    public String getClipboard() { return ""; }
}
''',

    "net/minecraft/client/KeyMapping.java": '''
package net.minecraft.client;
public class KeyMapping {
    public KeyMapping(String name, net.minecraftforge.client.settings.IKeyConflictContext ctx, com.mojang.blaze3d.platform.InputConstants.Type t, int key, String category) { this(); }
    public KeyMapping(String name, net.minecraftforge.client.settings.IKeyConflictContext ctx, net.minecraftforge.client.settings.KeyModifier mod, com.mojang.blaze3d.platform.InputConstants.Type t, int key, String category) { this(); }
    public KeyMapping(String name, net.minecraftforge.client.settings.IKeyConflictContext ctx, int key, String category) { this(); }
    public KeyMapping() {}
    public boolean consumeClick() { return false; }
    public boolean isDown() { return false; }
    public String getName() { return ""; }
    public void setKey(int key) {}
    public int getKey() { return 0; }
}
''',

    # NOTE: net/minecraft/client/KeyConflictContext is intentionally NOT stubbed
    # so source code uses net.minecraftforge.client.settings.KeyConflictContext instead

    "net/minecraft/client/gui/Font.java": '''
package net.minecraft.client.gui;
public class Font {
    public int width(String s) { return s.length() * 6; }
    public int lineHeight = 9;
    public int getLineHeight() { return 9; }
    public void drawInBatch(String s, float x, float y, int color, boolean shadow,
                            com.mojang.blaze3d.vertex.PoseStack ps,
                            com.mojang.blaze3d.vertex.MultiBufferSource mbs, boolean p, int x1, int y1) {}
    public void drawInBatch(String s, float x, float y, int color, boolean shadow,
                            com.mojang.blaze3d.vertex.PoseStack ps,
                            com.mojang.blaze3d.vertex.MultiBufferSource mbs, boolean p, int x1, int y1, int z) {}
}
''',

    "net/minecraft/client/gui/GuiGraphics.java": '''
package net.minecraft.client.gui;
public class GuiGraphics {
    public com.mojang.blaze3d.vertex.PoseStack pose;
    public void drawString(net.minecraft.client.gui.Font f, String s, int x, int y, int color, boolean shadow) {}
    public void drawString(net.minecraft.client.gui.Font f, net.minecraft.network.chat.Component c, int x, int y, int color, boolean shadow) {}
    public void drawCenteredString(net.minecraft.client.gui.Font f, String s, int x, int y, int color) {}
    public void drawCenteredString(net.minecraft.client.gui.Font f, net.minecraft.network.chat.Component c, int x, int y, int color) {}
    public void fill(int x1, int y1, int x2, int y2, int color) {}
    public void blit(net.minecraft.resources.ResourceLocation rl, int x, int y, int u, int v, int w, int h) {}
    public void renderBackground(int v) {}
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int v) {}
    public void flush() {}
}
''',

    "net/minecraft/client/gui/components/AbstractWidget.java": '''
package net.minecraft.client.gui.components;
public abstract class AbstractWidget implements net.minecraft.client.gui.components.events.GuiEventListener {
    public int x, y, width, height;
    public void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float pt) {}
    public boolean mouseClicked(double mx, double my, int b) { return false; }
}
''',

    "net/minecraft/client/gui/components/events/GuiEventListener.java": '''
package net.minecraft.client.gui.components.events;
public interface GuiEventListener {
    default boolean mouseClicked(double x, double y, int b) { return false; }
    default boolean mouseScrolled(double x, double y, double d) { return false; }
    default boolean keyPressed(int k, int s, int m) { return false; }
    default boolean charTyped(char c, int m) { return false; }
    default void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float pt) {}
}
''',

    "net/minecraft/client/gui/components/Button.java": '''
package net.minecraft.client.gui.components;
public class Button extends net.minecraft.client.gui.components.AbstractWidget {
    public interface OnPress {
        void onPress(Button b);
    }
    public static Button.Builder builder(net.minecraft.network.chat.Component label, OnPress onPress) {
        return new Button.Builder(label, onPress);
    }
    public static class Builder {
        public Builder(net.minecraft.network.chat.Component label, OnPress onPress) {}
        public Builder bounds(int x, int y, int w, int h) { return this; }
        public Builder size(int w, int h) { return this; }
        public Builder pos(int x, int y) { return this; }
        public Button build() { return new Button(); }
    }
    public Button() {}
    public void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float pt) {}
}
''',

    "net/minecraft/client/gui/screens/Screen.java": '''
package net.minecraft.client.gui.screens;
public abstract class Screen implements net.minecraft.client.gui.components.events.GuiEventListener {
    protected net.minecraft.client.Minecraft minecraft;
    protected net.minecraft.client.gui.Font font;
    public int width, height;
    public Screen(net.minecraft.network.chat.Component title) {}
    // SRG 名方法（production 环境用）
    protected void m_7856_() {}
    public void m_88315_(net.minecraft.client.gui.GuiGraphics g, int x, int y, float pt) {}
    public boolean m_7933_(int k, int s, int m) { return false; }
    public boolean m_5534_(char c, int m) { return false; }
    public boolean m_6375_(double x, double y, int b) { return false; }
    public boolean m_6050_(double x, double y, double d) { return false; }
    public void m_7379_() {}
    public boolean m_7043_() { return true; }
    public void m_6574_(net.minecraft.client.Minecraft mc, int w, int h) {}
    // deobf 名方法（dev 环境用）
    protected void init() {}
    public void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float pt) {}
    public boolean keyPressed(int k, int s, int m) { return false; }
    public boolean charTyped(char c, int m) { return false; }
    public boolean mouseClicked(double x, double y, int b) { return false; }
    public boolean mouseScrolled(double x, double y, double d) { return false; }
    public void onClose() {}
    public boolean isPauseScreen() { return true; }
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {}
    protected <T extends net.minecraft.client.gui.components.AbstractWidget> T addRenderableWidget(T w) { return w; }
    protected <T> T addWidget(T w) { return w; }
    public net.minecraft.network.chat.Component getTitle() { return null; }
}
''',

    "net/minecraft/client/player/LocalPlayer.java": '''
package net.minecraft.client.player;
public class LocalPlayer {
    public boolean isLocalPlayer() { return true; }
}
''',

    "net/minecraft/commands/CommandSourceStack.java": '''
package net.minecraft.commands;
public class CommandSourceStack {
    public void sendSuccess(java.util.function.Supplier<net.minecraft.network.chat.Component> s, boolean b) {}
    public void sendFailure(net.minecraft.network.chat.Component c) {}
}
''',

    "net/minecraft/commands/Commands.java": '''
package net.minecraft.commands;
public class Commands {
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(String s) {
        return null;
    }
    public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, T> argument(String s, com.mojang.brigadier.arguments.ArgumentType<T> t) {
        return null;
    }
    public void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d) {}
}
''',

    "net/minecraft/network/chat/Component.java": '''
package net.minecraft.network.chat;
public interface Component {
    static Component translatable(String s) { return new Component() {}; }
    static Component literal(String s) { return new Component() {}; }
    static Component empty() { return new Component() {}; }
    default String getString() { return ""; }
}
''',

    "net/minecraft/resources/ResourceLocation.java": '''
package net.minecraft.resources;
public class ResourceLocation {
    public ResourceLocation(String ns, String path) {}
    public ResourceLocation(String path) {}
    public String getNamespace() { return "minecraft"; }
    public String getPath() { return ""; }
}
''',

    # ============ Mojang (com.mojang.*) ============
    "com/mojang/blaze3d/platform/InputConstants.java": '''
package com.mojang.blaze3d.platform;
public class InputConstants {
    public enum Type {
        KEYSYM, SCANCODE, MOUSE;
        public InputConstants.Key get(int code) { return null; }
    }
    public static class Key {
        public int getValue() { return 0; }
    }
}
''',

    "com/mojang/blaze3d/vertex/PoseStack.java": '''
package com.mojang.blaze3d.vertex;
public class PoseStack {
    public void pushPose() {}
    public void popPose() {}
    public void translate(double x, double y, double z) {}
    public void scale(float x, float y, float z) {}
}
''',

    "com/mojang/blaze3d/vertex/MultiBufferSource.java": '''
package com.mojang.blaze3d.vertex;
public interface MultiBufferSource {
    public static MultiBufferSource.BufferSource immediate(com.mojang.blaze3d.vertex.VertexConsumer vc) { return null; }
    public interface BufferSource extends MultiBufferSource {
        void endBatch();
    }
}
''',

    "com/mojang/blaze3d/vertex/VertexConsumer.java": '''
package com.mojang.blaze3d.vertex;
public interface VertexConsumer {}
''',

    "com/mojang/blaze3d/systems/RenderSystem.java": '''
package com.mojang.blaze3d.systems;
public class RenderSystem {
    public static void enableBlend() {}
    public static void disableBlend() {}
    public static void setShaderColor(float r, float g, float b, float a) {}
}
''',

    # ============ Forge (net.minecraftforge.*) ============
    "net/minecraftforge/common/ForgeConfigSpec.java": '''
package net.minecraftforge.common;
import java.util.List;
import java.util.function.Predicate;
public class ForgeConfigSpec implements net.minecraftforge.fml.config.IConfigSpec<ForgeConfigSpec> {
    public static class Builder {
        public Builder comment(String... c) { return this; }
        public Builder push(String s) { return this; }
        public Builder pop() { return this; }
        public Builder push(List<String> l) { return this; }
        public <T> ConfigValue<T> define(String path, T defaultValue) { return null; }
        public <T> ConfigValue<T> define(String path, T defaultValue, Predicate<T> validator) { return null; }
        public ConfigValue<String> define(String path, String defaultValue) { return null; }
        public IntValue defineInRange(String path, int def, int min, int max) { return null; }
        public LongValue defineInRange(String path, long def, long min, long max) { return null; }
        public DoubleValue defineInRange(String path, double def, double min, double max) { return null; }
        public BooleanValue define(String path, boolean def) { return null; }
        public BooleanValue define(String path, boolean def, Predicate<Boolean> v) { return null; }
        public ForgeConfigSpec build() { return new ForgeConfigSpec(); }
    }
    public ForgeConfigSpec() {}
    public void save() {}
    public static abstract class ConfigValue<T> {
        public T get() { return null; }
        public void set(T v) {}
        public void save() {}
    }
    public static abstract class IntValue extends ConfigValue<Integer> {
        public Integer get() { return 0; }
    }
    public static abstract class LongValue extends ConfigValue<Long> {
        public Long get() { return 0L; }
    }
    public static abstract class DoubleValue extends ConfigValue<Double> {
        public Double get() { return 0.0; }
    }
    public static abstract class BooleanValue extends ConfigValue<Boolean> {
        public Boolean get() { return false; }
    }
    // 实现 IConfigSpec 接口（为了让 ForgeConfigSpec 能传入 registerConfig(Type, IConfigSpec)）
    public void acceptConfig(com.electronwill.nightconfig.core.CommentedConfig config) {}
    public boolean isCorrecting() { return false; }
    public boolean isCorrect(com.electronwill.nightconfig.core.CommentedConfig config) { return true; }
    public int correct(com.electronwill.nightconfig.core.CommentedConfig config) { return 0; }
    public void afterReload() {}
    public ForgeConfigSpec self() { return this; }
}
''',

    "net/minecraftforge/common/MinecraftForge.java": '''
package net.minecraftforge.common;
public class MinecraftForge {
    public static net.minecraftforge.eventbus.api.IEventBus EVENT_BUS = null;
}
''',

    "net/minecraftforge/eventbus/api/IEventBus.java": '''
package net.minecraftforge.eventbus.api;
public interface IEventBus {
    <T extends Event> void addListener(java.util.function.Consumer<T> listener);
    <T extends Event> void addListener(net.minecraftforge.eventbus.api.EventPriority priority, java.util.function.Consumer<T> listener);
    <T extends Event> void addListener(boolean receiveCanceled, java.util.function.Consumer<T> listener);
    <T extends Event> void addListener(net.minecraftforge.eventbus.api.EventPriority priority, boolean receiveCanceled, java.util.function.Consumer<T> listener);
    void addGenericListener(Class<?> genericClass, java.util.function.Consumer<? extends GenericEvent<?>> listener);
    void register(Object target);
    boolean post(Event event);
}
''',

    "net/minecraftforge/eventbus/api/EventPriority.java": '''
package net.minecraftforge.eventbus.api;
public enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST; }
''',

    "net/minecraftforge/eventbus/api/Event.java": '''
package net.minecraftforge.eventbus.api;
public class Event {
    public static class Result { public static final Result ALLOW = null, DENY = null, DEFAULT = null; }
    public Result getResult() { return Result.DEFAULT; }
    public boolean isCancelable() { return false; }
    public boolean isCanceled() { return false; }
    public void setCanceled(boolean c) {}
}
''',

    "net/minecraftforge/eventbus/api/SubscribeEvent.java": '''
package net.minecraftforge.eventbus.api;
public @interface SubscribeEvent {
}''',

    "net/minecraftforge/eventbus/api/GenericEvent.java": '''
package net.minecraftforge.eventbus.api;
public class GenericEvent<T> extends Event {
}
''',

    "net/minecraftforge/event/TickEvent.java": '''
package net.minecraftforge.event;
public class TickEvent extends net.minecraftforge.eventbus.api.Event {
    public enum Phase { START, END; }
    public Phase phase;
    public static class ClientTickEvent extends TickEvent {
        public Phase phase;
    }
    public static class ServerTickEvent extends TickEvent {
        public Phase phase;
    }
}
''',

    "net/minecraftforge/event/RegisterCommandsEvent.java": '''
package net.minecraftforge.event;
public class RegisterCommandsEvent extends net.minecraftforge.eventbus.api.Event {
    public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher() { return null; }
}
''',

    "net/minecraftforge/event/server/ServerStartingEvent.java": '''
package net.minecraftforge.event.server;
public class ServerStartingEvent extends net.minecraftforge.eventbus.api.Event {
    public net.minecraft.server.MinecraftServer getServer() { return null; }
}
''',

    "net/minecraftforge/server/MinecraftServer.java": '''
package net.minecraft.server;
public class MinecraftServer {
    public boolean isDedicatedServer() { return false; }
}
''',

    "net/minecraftforge/fml/ModLoadingContext.java": '''
package net.minecraftforge.fml;
public class ModLoadingContext {
    public static ModLoadingContext get() { return new ModLoadingContext(); }
    // Forge 1.20.1 真实签名：registerConfig(Type, IConfigSpec<?>) — 接受接口而非具体类
    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec<?> spec) {}
    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec<?> spec, String fileName) {}
}
''',

    "net/minecraftforge/fml/config/IConfigSpec.java": '''
package net.minecraftforge.fml.config;
public interface IConfigSpec<T extends IConfigSpec<T>> {
    void acceptConfig(com.electronwill.nightconfig.core.CommentedConfig config);
    boolean isCorrecting();
    boolean isCorrect(com.electronwill.nightconfig.core.CommentedConfig config);
    int correct(com.electronwill.nightconfig.core.CommentedConfig config);
    void afterReload();
}
''',

    "com/electronwill/nightconfig/core/CommentedConfig.java": '''
package com.electronwill.nightconfig.core;
public interface CommentedConfig extends UnmodifiableConfig {
}
''',

    "com/electronwill/nightconfig/core/UnmodifiableConfig.java": '''
package com.electronwill.nightconfig.core;
public interface UnmodifiableConfig {
}
''',

    "net/minecraftforge/fml/config/ModConfig.java": '''
package net.minecraftforge.fml.config;
public class ModConfig {
    public enum Type { COMMON, CLIENT, SERVER; }
}
''',

    "net/minecraftforge/fml/common/Mod.java": '''
package net.minecraftforge.fml.common;
public @interface Mod {
    String value();
    String modid() default "";
    String[] valueArr() default {};
    public @interface EventBusSubscriber {
        String modid() default "";
        Bus bus() default Bus.FORGE;
        public enum Bus { FORGE, MOD; }
    }
}
''',

    "net/minecraftforge/fml/EventBusSubscriber.java": '''
package net.minecraftforge.fml;
public @interface EventBusSubscriber {
    String modid() default "";
    Bus bus() default Bus.FORGE;
    public enum Bus { FORGE, MOD; }
}
''',

    "net/minecraftforge/fml/loading/FMLLoader.java": '''
package net.minecraftforge.fml.loading;
public class FMLLoader {
    public static boolean isDistSafe() { return false; }
    public static String getDistName() { return "CLIENT"; }
}
''',

    "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext.java": '''
package net.minecraftforge.fml.javafmlmod;
public class FMLJavaModLoadingContext {
    public static FMLJavaModLoadingContext get() { return new FMLJavaModLoadingContext(); }
    public net.minecraftforge.eventbus.api.IEventBus getModEventBus() { return null; }
}
''',

    "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent.java": '''
package net.minecraftforge.fml.event.lifecycle;
public class FMLCommonSetupEvent extends net.minecraftforge.eventbus.api.Event implements net.minecraftforge.fml.event.IModBusEvent {
    public void enqueueWork(Runnable r) {}
}
''',

    "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent.java": '''
package net.minecraftforge.fml.event.lifecycle;
public class FMLClientSetupEvent extends net.minecraftforge.eventbus.api.Event implements net.minecraftforge.fml.event.IModBusEvent {
    public void enqueueWork(Runnable r) {}
}
''',

    "net/minecraftforge/fml/event/IModBusEvent.java": '''
package net.minecraftforge.fml.event;
public interface IModBusEvent {
}
''',

    "net/minecraftforge/client/event/RegisterKeyMappingsEvent.java": '''
package net.minecraftforge.client.event;
public class RegisterKeyMappingsEvent extends net.minecraftforge.eventbus.api.Event implements net.minecraftforge.fml.event.IModBusEvent {
    public void register(net.minecraft.client.KeyMapping km) {}
}
''',

    "net/minecraftforge/client/event/InputEvent.java": '''
package net.minecraftforge.client.event;
public class InputEvent extends net.minecraftforge.eventbus.api.Event {
    public static class Key extends InputEvent {
        private final int key;
        private final int scanCode;
        private final int action;
        private final int modifiers;
        public Key(int key, int scanCode, int action, int modifiers) {
            this.key = key; this.scanCode = scanCode;
            this.action = action; this.modifiers = modifiers;
        }
        public int getKey() { return key; }
        public int getScanCode() { return scanCode; }
        public int getAction() { return action; }
        public int getModifiers() { return modifiers; }
    }
    public static class MouseInputEvent extends InputEvent {
        public int getButton() { return 0; }
        public int getAction() { return 0; }
        public int getModifiers() { return 0; }
    }
    public static class MouseScrollingEvent extends InputEvent {
        public double getScrollDelta() { return 0; }
        public double getMouseX() { return 0; }
        public double getMouseY() { return 0; }
        public int getModifiers() { return 0; }
    }
}
''',

    "net/minecraftforge/client/settings/KeyConflictContext.java": '''
package net.minecraftforge.client.settings;
public enum KeyConflictContext implements IKeyConflictContext {
    IN_GAME, IN_GUI, UNIVERSAL, ANY;
    public boolean isActive() { return true; }
    public boolean conflicts(IKeyConflictContext other) { return false; }
}
''',

    "net/minecraftforge/client/settings/IKeyConflictContext.java": '''
package net.minecraftforge.client.settings;
public interface IKeyConflictContext {
    boolean isActive();
    boolean conflicts(IKeyConflictContext other);
}
''',

    "net/minecraftforge/client/settings/KeyModifier.java": '''
package net.minecraftforge.client.settings;
public enum KeyModifier {
    NONE, CONTROL, SHIFT, ALT;
    public boolean matches(int key) { return false; }
}
''',

    # LWJGL GLFW (provided by Forge runtime)
    "org/lwjgl/glfw/GLFW.java": '''
package org.lwjgl.glfw;
public class GLFW {
    public static final int GLFW_MOD_SHIFT = 1;
    public static final int GLFW_MOD_CONTROL = 2;
    public static final int GLFW_MOD_ALT = 4;
    public static final int GLFW_MOD_SUPER = 8;
    public static final int GLFW_RELEASE = 0;
    public static final int GLFW_PRESS = 1;
    public static final int GLFW_REPEAT = 2;
    public static final int GLFW_KEY_UNKNOWN = -1;
    public static final int GLFW_KEY_ESCAPE = 256;
    public static final int GLFW_KEY_ENTER = 257;
    public static final int GLFW_KEY_TAB = 258;
    public static final int GLFW_KEY_BACKSPACE = 259;
    public static final int GLFW_KEY_INSERT = 260;
    public static final int GLFW_KEY_DELETE = 261;
    public static final int GLFW_KEY_RIGHT = 262;
    public static final int GLFW_KEY_LEFT = 263;
    public static final int GLFW_KEY_DOWN = 264;
    public static final int GLFW_KEY_UP = 265;
    public static final int GLFW_KEY_PAGE_UP = 266;
    public static final int GLFW_KEY_PAGE_DOWN = 267;
    public static final int GLFW_KEY_HOME = 268;
    public static final int GLFW_KEY_END = 269;
    public static final int GLFW_KEY_F1 = 290;
    public static final int GLFW_KEY_F2 = 291;
    public static final int GLFW_KEY_F3 = 292;
    public static final int GLFW_KEY_F4 = 293;
    public static final int GLFW_KEY_F5 = 294;
    public static final int GLFW_KEY_F6 = 295;
    public static final int GLFW_KEY_F7 = 296;
    public static final int GLFW_KEY_F8 = 297;
    public static final int GLFW_KEY_F9 = 298;
    public static final int GLFW_KEY_F10 = 299;
    public static final int GLFW_KEY_F11 = 300;
    public static final int GLFW_KEY_F12 = 301;
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    public static final int GLFW_KEY_LEFT_CONTROL = 341;
    public static final int GLFW_KEY_LEFT_ALT = 342;
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    public static final int GLFW_KEY_RIGHT_ALT = 346;
    public static final int GLFW_KEY_A = 65;
    public static final int GLFW_KEY_B = 66;
    public static final int GLFW_KEY_C = 67;
    public static final int GLFW_KEY_D = 68;
    public static final int GLFW_KEY_E = 69;
    public static final int GLFW_KEY_F = 70;
    public static final int GLFW_KEY_G = 71;
    public static final int GLFW_KEY_H = 72;
    public static final int GLFW_KEY_I = 73;
    public static final int GLFW_KEY_J = 74;
    public static final int GLFW_KEY_K = 75;
    public static final int GLFW_KEY_L = 76;
    public static final int GLFW_KEY_M = 77;
    public static final int GLFW_KEY_N = 78;
    public static final int GLFW_KEY_O = 79;
    public static final int GLFW_KEY_P = 80;
    public static final int GLFW_KEY_Q = 81;
    public static final int GLFW_KEY_R = 82;
    public static final int GLFW_KEY_S = 83;
    public static final int GLFW_KEY_T = 84;
    public static final int GLFW_KEY_U = 85;
    public static final int GLFW_KEY_V = 86;
    public static final int GLFW_KEY_W = 87;
    public static final int GLFW_KEY_X = 88;
    public static final int GLFW_KEY_Y = 89;
    public static final int GLFW_KEY_Z = 90;
    public static final int GLFW_KEY_LEFT_BRACKET = 91;
    public static final int GLFW_KEY_BACKSLASH = 92;
    public static final int GLFW_KEY_RIGHT_BRACKET = 93;
    public static final int GLFW_KEY_SLASH = 95;
    // 静态方法（LWJGL GLFW 真实 API）
    public static int glfwGetKey(long window, int key) { return GLFW_RELEASE; }
    public static long glfwGetCurrentContext() { return 0L; }
    public static int glfwGetMouseButton(long window, int button) { return GLFW_RELEASE; }
    public static void glfwSetCursorPos(long window, double x, double y) {}
}
''',

    # Mojang Brigadier (provided by Forge)
    "com/mojang/brigadier/CommandDispatcher.java": '''
package com.mojang.brigadier;
public class CommandDispatcher<S> {
    public com.mojang.brigadier.builder.LiteralArgumentBuilder<S> register(com.mojang.brigadier.builder.LiteralArgumentBuilder<S> b) { return b; }
}
''',

    "com/mojang/brigadier/builder/LiteralArgumentBuilder.java": '''
package com.mojang.brigadier.builder;
public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
    public static <S> LiteralArgumentBuilder<S> literal(String s) { return new LiteralArgumentBuilder<>(); }
}
''',

    "com/mojang/brigadier/builder/ArgumentBuilder.java": '''
package com.mojang.brigadier.builder;
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T executes(com.mojang.brigadier.Command<S> cmd) { return (T) this; }
    public ArgumentBuilder<S, ?> then(ArgumentBuilder<S, ?> arg) { return arg; }
    public T then(LiteralArgumentBuilder<S> arg) { return (T) this; }
    public T then(RequiredArgumentBuilder<S, ?> arg) { return (T) this; }
}
''',

    "com/mojang/brigadier/builder/RequiredArgumentBuilder.java": '''
package com.mojang.brigadier.builder;
public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
    public static <S, T> RequiredArgumentBuilder<S, T> argument(String s, com.mojang.brigadier.arguments.ArgumentType<T> t) { return new RequiredArgumentBuilder<>(); }
}
''',

    "com/mojang/brigadier/Command.java": '''
package com.mojang.brigadier;
public interface Command<S> {
    int run(com.mojang.brigadier.context.CommandContext<S> ctx) throws Exception;
}
''',

    "com/mojang/brigadier/context/CommandContext.java": '''
package com.mojang.brigadier.context;
public class CommandContext<S> {
    public S getSource() { return null; }
}
''',

    "com/mojang/brigadier/arguments/ArgumentType.java": '''
package com.mojang.brigadier.arguments;
public interface ArgumentType<T> {}
''',

    # Apache Log4J (provided by Forge)
    "org/apache/logging/log4j/Logger.java": '''
package org.apache.logging.log4j;
public interface Logger {
    void info(CharSequence msg);
    void info(CharSequence msg, Object... args);
    void info(String msg, Object... args);
    void info(String msg, Throwable t);
    void info(Object msg);
    void warn(CharSequence msg);
    void warn(CharSequence msg, Object... args);
    void warn(String msg, Object... args);
    void warn(String msg, Throwable t);
    void warn(Object msg);
    void error(CharSequence msg);
    void error(CharSequence msg, Object... args);
    void error(String msg, Object... args);
    void error(String msg, Throwable t);
    void error(Object msg);
    void debug(CharSequence msg);
    void debug(CharSequence msg, Object... args);
    void debug(String msg, Object... args);
    void debug(String msg, Throwable t);
    void debug(Object msg);
    void trace(CharSequence msg);
    void trace(CharSequence msg, Object... args);
    void trace(String msg, Object... args);
    void trace(String msg, Throwable t);
    void trace(Object msg);
}
''',

    "org/apache/logging/log4j/LogManager.java": '''
package org.apache.logging.log4j;
public class LogManager {
    public static Logger getLogger(String name) { return new Logger() {
        public void info(CharSequence m) {}
        public void info(CharSequence m, Object... a) {}
        public void info(String m, Object... a) {}
        public void info(String m, Throwable t) {}
        public void info(Object m) {}
        public void warn(CharSequence m) {}
        public void warn(CharSequence m, Object... a) {}
        public void warn(String m, Object... a) {}
        public void warn(String m, Throwable t) {}
        public void warn(Object m) {}
        public void error(CharSequence m) {}
        public void error(CharSequence m, Object... a) {}
        public void error(String m, Object... a) {}
        public void error(String m, Throwable t) {}
        public void error(Object m) {}
        public void debug(CharSequence m) {}
        public void debug(CharSequence m, Object... a) {}
        public void debug(String m, Object... a) {}
        public void debug(String m, Throwable t) {}
        public void debug(Object m) {}
        public void trace(CharSequence m) {}
        public void trace(CharSequence m, Object... a) {}
        public void trace(String m, Object... a) {}
        public void trace(String m, Throwable t) {}
        public void trace(Object m) {}
    }; }
}
''',

    # JetBrains Annotations
    "org/jetbrains/annotations/NotNull.java": '''
package org.jetbrains.annotations;
public @interface NotNull {}
''',
    "org/jetbrains/annotations/Nullable.java": '''
package org.jetbrains.annotations;
public @interface Nullable {}
''',

    # ============ jediterm 补充 stubs (该版本缺失的类) ============
    "com/jediterm/core/util/TerminalSize.java": '''
package com.jediterm.core.util;
public class TerminalSize {
    private final int cols, rows;
    public TerminalSize(int cols, int rows) { this.cols = cols; this.rows = rows; }
    public int getColumns() { return cols; }
    public int getRows() { return rows; }
    public int getHeight() { return rows; }
    public int getWidth() { return cols; }
}
''',

    "com/jediterm/core/Color.java": '''
package com.jediterm.core;
public class Color {
    public Color(int r, int g, int b) {}
    public Color(int rgb) {}
    public int getRed() { return 0; }
    public int getGreen() { return 0; }
    public int getBlue() { return 0; }
    public int getRGB() { return 0; }
}
''',

    "com/jediterm/terminal/TextStyle.java": '''
package com.jediterm.terminal;
public class TextStyle {
    public TextStyle() {}
    public TextStyle(com.jediterm.terminal.TerminalColor fg, com.jediterm.terminal.TerminalColor bg) {}
    public static TextStyle getEmpty() { return new TextStyle(); }
}
''',

    "com/jediterm/terminal/TerminalColor.java": '''
package com.jediterm.terminal;
public class TerminalColor {
    public static TerminalColor rgb(com.jediterm.core.Color c) { return new TerminalColor(); }
    public static TerminalColor index(int i) { return new TerminalColor(); }
}
''',

    "com/jediterm/terminal/CursorShape.java": '''
package com.jediterm.terminal;
public enum CursorShape {
    BLOCK, UNDERSCORE, BAR, BLINK_BLOCK, BLINK_UNDERSCORE, BLINK_BAR;
}
''',

    "com/jediterm/terminal/TextStyleChangeEvent.java": '''
package com.jediterm.terminal;
public class TextStyleChangeEvent {
}
''',

    "com/jediterm/terminal/model/Line.java": '''
package com.jediterm.terminal.model;
// CharBuffer is in jediterm-core, use qualified name with stub fallback
public class Line {
    public Object getText() { return null; }
}
''',

    "com/jediterm/terminal/ui/settings/SettingsProvider.java": '''
package com.jediterm.terminal.ui.settings;
public interface SettingsProvider {
    com.jediterm.terminal.TextStyle getDefaultStyle();
    com.jediterm.terminal.TextStyle getSelectionColor();
    com.jediterm.terminal.TextStyle getFoundPatternColor();
    com.jediterm.terminal.TextStyle getSearchMatchColor();
    int getBufferMaxLinesCount();
    boolean useInverseSelectionColor();
    boolean emulateX11CopyPaste();
    boolean copyOnSelect();
    boolean pasteOnMiddleMouseClick();
    boolean mouseWheelReporting();
    boolean sendArrowKeysInAlternativeMode();
    boolean altSendsEscape();
    boolean enableBracketedPasteMode();
    java.awt.Dimension getSelectionAnimationColor();
    int getSelectionAnimationDurationMs();
    com.jediterm.terminal.CursorShape getCursorShape();
    boolean isCursorBlinking();
    boolean blinkOn();
    int getBlinkingPeriod();
    int getMaxRefreshRate();
    boolean shouldDrawBoldText();
    boolean useAntiAliasing();
    java.awt.Font getNormalFont();
    java.awt.Font getBoldFont();
    float getLineSpace();
    boolean shouldFillViewport();
    java.awt.Color getWindowBackground();
    java.awt.Color getWindowForeground();
}
''',

    "com/jediterm/terminal/ui/settings/DefaultSettingsProvider.java": '''
package com.jediterm.terminal.ui.settings;
public class DefaultSettingsProvider implements SettingsProvider {
    public DefaultSettingsProvider() {}
    public com.jediterm.terminal.TextStyle getDefaultStyle() { return null; }
    public com.jediterm.terminal.TextStyle getSelectionColor() { return null; }
    public com.jediterm.terminal.TextStyle getFoundPatternColor() { return null; }
    public com.jediterm.terminal.TextStyle getSearchMatchColor() { return null; }
    public int getBufferMaxLinesCount() { return 5000; }
    public boolean useInverseSelectionColor() { return false; }
    public boolean emulateX11CopyPaste() { return false; }
    public boolean copyOnSelect() { return false; }
    public boolean pasteOnMiddleMouseClick() { return false; }
    public boolean mouseWheelReporting() { return false; }
    public boolean sendArrowKeysInAlternativeMode() { return true; }
    public boolean altSendsEscape() { return false; }
    public boolean enableBracketedPasteMode() { return false; }
    public java.awt.Dimension getSelectionAnimationColor() { return null; }
    public int getSelectionAnimationDurationMs() { return 0; }
    public com.jediterm.terminal.CursorShape getCursorShape() { return null; }
    public boolean isCursorBlinking() { return false; }
    public boolean blinkOn() { return false; }
    public int getBlinkingPeriod() { return 500; }
    public int getMaxRefreshRate() { return 0; }
    public boolean shouldDrawBoldText() { return false; }
    public boolean useAntiAliasing() { return false; }
    public java.awt.Font getNormalFont() { return null; }
    public java.awt.Font getBoldFont() { return null; }
    public float getLineSpace() { return 0; }
    public boolean shouldFillViewport() { return false; }
    public java.awt.Color getWindowBackground() { return null; }
    public java.awt.Color getWindowForeground() { return null; }
}
''',

    "com/jediterm/terminal/ui/settings/TabbedSettingsProvider.java": '''
package com.jediterm.terminal.ui.settings;
public interface TabbedSettingsProvider extends SettingsProvider {
}
''',

    "com/jediterm/terminal/model/hyperlinks/HyperlinkFilter.java": '''
package com.jediterm.terminal.model.hyperlinks;
public interface HyperlinkFilter {
}
''',

    "com/jediterm/terminal/model/TerminalSelection.java": '''
package com.jediterm.terminal.model;
public class TerminalSelection {
    public TerminalSelection() {}
    public Object getStart() { return null; }
    public Object getEnd() { return null; }
}
''',

    "com/jediterm/terminal/model/TerminalCursorState.java": '''
package com.jediterm.terminal.model;
public class TerminalCursorState {
    public int getX() { return 0; }
    public int getY() { return 0; }
}
''',

    "com/jediterm/terminal/TerminalMode.java": '''
package com.jediterm.terminal;
public enum TerminalMode {
    Null, CursorKey, AnsiConformanceLevel1, AnsiConformanceLevel2, AnsiConformanceLevel3,
    InsertMode, CursorVisible, AutoNewLine, ReverseVideo, OriginMode,
    ScrollRegion, ColumnMode, WraparoundMode, AutoRepeatKeys, LightBackground,
    AlternativeViewport, ApplicationKeypad, StoreCursor, SaveCursor,
    BracketedPasteMode, FocusReportingMode, Mouse1000, Mouse1001, Mouse1002,
    Mouse1003, Mouse1005, Mouse1006, Mouse1015, MouseSgrMotion,
    SendFocus, ExtendedAltKeys;
    public boolean isEnabled() { return false; }
}
''',

    "com/jediterm/terminal/Questioner.java": '''
package com.jediterm.terminal;
public interface Questioner {
    void showQuestion(String message, java.util.function.Consumer<String> callback);
    void showMessage(String message);
}
''',

    "com/jediterm/core/typeahead/TerminalTypeAheadManager.java": '''
package com.jediterm.core.typeahead;
public class TerminalTypeAheadManager {
    public TerminalTypeAheadManager(TypeAheadTerminalModel model) {}
    public static TerminalTypeAheadManager createDefault(Object terminal, Object ttyConnector) {
        return new TerminalTypeAheadManager(null);
    }
    public void lock() {}
    public void unlock() {}
    public void onTerminalStateChanged() {}
}
''',

    "com/jediterm/core/typeahead/TypeAheadTerminalModel.java": '''
package com.jediterm.core.typeahead;
public interface TypeAheadTerminalModel {
    void insertCharacter(char c, int x);
    void removeCharacters(int x, int count);
    void moveCursor(int x);
    void forceRedraw();
    void clearPredictions();
    void lock();
    void unlock();
    boolean isUsingAlternateBuffer();
    Object getCurrentLineWithCursor();
    int getTerminalWidth();
    boolean isTypeAheadEnabled();
    long getLatencyThreshold();
    Object getShellType();
}
''',

    "com/jediterm/terminal/TerminalExecutorServiceManager.java": '''
package com.jediterm.terminal;
public class TerminalExecutorServiceManager {
    public TerminalExecutorServiceManager() {}
    public java.util.concurrent.ScheduledExecutorService getSingleThreadScheduledExecutor() { return null; }
    public java.util.concurrent.ExecutorService getUnboundedExecutorService() { return null; }
    public void shutdownWhenAllExecuted() {}
}
''',

    "com/jediterm/terminal/RequestOrigin.java": '''
package com.jediterm.terminal;
public enum RequestOrigin { User, Remote, Other; }
''',

    "com/jediterm/terminal/emulator/mouse/MouseMode.java": '''
package com.jediterm.terminal.emulator.mouse;
public enum MouseMode {
    MOUSE_REPORTING_NONE, MOUSE_REPORTING_NORMAL, MOUSE_REPORTING_BUTTON_EVENT,
    MOUSE_REPORTING_ALL_MOTION;
}
''',

    "com/jediterm/terminal/emulator/mouse/MouseFormat.java": '''
package com.jediterm.terminal.emulator.mouse;
public enum MouseFormat { X10, NORMAL, UTF8, SGR, URXVT; }
''',
}

def main():
    src_dir = Path("/tmp/mc-stubs-src")
    out_dir = Path("/tmp/mc-stubs-classes")
    src_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Write stub sources
    written = []
    for rel_path, content in STUBS.items():
        f = src_dir / rel_path
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(content)
        written.append(rel_path)

    print(f"Written {len(written)} stub sources to {src_dir}")

    # Compile all stubs
    cmd = [
        str(JAVAC), "-d", str(out_dir),
        "-source", "17", "-target", "17",
        "-proc:none", "-nowarn", "-Xlint:none",
        "-encoding", "UTF-8",
    ]
    for rel in written:
        cmd.append(str(src_dir / rel))

    print(f"Compiling stubs with javac...")
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if result.returncode != 0:
        print("STDOUT:", result.stdout)
        print("STDERR:", result.stderr)
        sys.exit(1)
    print(f"Compiled {len(written)} stub classes to {out_dir}")

    # Package into jar
    jar_path = "/home/z/tools/libs/mc-stubs.jar"
    cmd = [str(JAR), "cf", jar_path, "-C", str(out_dir), "."]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        print("JAR STDERR:", result.stderr)
        sys.exit(1)
    print(f"Created {jar_path}")

if __name__ == "__main__":
    main()
