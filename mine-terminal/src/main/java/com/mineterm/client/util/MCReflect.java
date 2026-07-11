package com.mineterm.client.util;

import com.mineterm.MineTerminal;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft 反射工具类（集中管理 SRG 混淆名映射）。
 *
 * 所有 SRG 名来自 Forge 1.20.1 MCPConfig joined.tsrg + Mojang client.txt。
 *
 * SRG 名映射表（已验证）：
 *   Minecraft.getInstance() → m_91087_
 *   Minecraft.setScreen(Screen) → m_91152_
 *   Minecraft.player → f_91074_
 *   Minecraft.screen → f_91080_
 *   Component.translatable(String) → m_237115_
 *   Component.literal(String) → m_237113_
 *   Component.empty() → m_237119_
 *   Font.width(String) → m_92895_
 *   GuiGraphics.fill(5int) → m_280509_
 *   GuiGraphics.drawString(Font,String,3int,boolean) → m_280056_
 *   GuiGraphics.drawString(Font,String,3int) → m_280488_
 *   GuiGraphics.drawCenteredString(Font,String,3int) → m_280137_
 *   Screen.onClose() → m_7379_
 *   Screen.addRenderableWidget(GuiEventListener) → m_142416_
 *   Button.builder(Component,OnPress) → m_253074_
 *   Button$Builder.bounds(int,int,int,int) → m_252987_
 *   Button$Builder.build() → m_253136_
 *   KeyboardHandler.setClipboard(String) → m_90911_
 *   KeyboardHandler.getClipboard() → m_90876_
 */
public class MCReflect {

    private static final Logger LOG = MineTerminal.LOGGER;

    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Field> fieldCache = new HashMap<>();

    private MCReflect() {}

    // ==================== Minecraft ====================
    private static final Class<?> MC_CLASS = net.minecraft.client.Minecraft.class;

    public static MinecraftHolder getMinecraft() {
        Method m = findMethod(MC_CLASS, "m_91087_", "getInstance");
        if (m == null) return null;
        try {
            Object mc = m.invoke(null);
            return mc != null ? new MinecraftHolder(mc) : null;
        } catch (Throwable t) { return null; }
    }

    public static class MinecraftHolder {
        public final Object mc;
        public MinecraftHolder(Object mc) { this.mc = mc; }

        public Object getPlayer() { return getFieldValue(mc, MC_CLASS, "f_91074_", "player"); }
        public Object getScreen() { return getFieldValue(mc, MC_CLASS, "f_91080_", "screen"); }
        public Object getFont() { return getFieldValue(mc, MC_CLASS, "f_91065_", "font"); }
        public Object getKeyboard_handler() { return getFieldValue(mc, MC_CLASS, "f_91066_", "keyboardHandler"); }

        public void setScreen(Object screen) {
            Method m = findMethod(MC_CLASS, "m_91152_", "setScreen",
                    net.minecraft.client.gui.screens.Screen.class);
            if (m != null) {
                try { m.invoke(mc, screen); } catch (Throwable t) {
                    LOG.warn("[Mine-Terminal] setScreen invoke failed: {}", t.getMessage());
                }
            }
        }

        public void execute(Runnable r) {
            Method m = findMethod(MC_CLASS, "m_18695_", "execute", Runnable.class);
            if (m != null) {
                try { m.invoke(mc, r); return; } catch (Throwable ignore) {}
            }
            r.run();
        }
    }

    // ==================== Component ====================
    private static final Class<?> COMPONENT_CLASS = net.minecraft.network.chat.Component.class;

    public static Object componentTranslatable(String key) {
        Method m = findMethod(COMPONENT_CLASS, "m_237115_", "translatable", String.class);
        if (m == null) return null;
        try { return m.invoke(null, key); } catch (Throwable t) { return null; }
    }

    public static Object componentLiteral(String text) {
        Method m = findMethod(COMPONENT_CLASS, "m_237113_", "literal", String.class);
        if (m == null) return null;
        try { return m.invoke(null, text); } catch (Throwable t) { return null; }
    }

    public static Object componentEmpty() {
        Method m = findMethod(COMPONENT_CLASS, "m_237119_", "empty");
        if (m == null) return null;
        try { return m.invoke(null); } catch (Throwable t) { return null; }
    }

    // ==================== Font ====================
    private static final Class<?> FONT_CLASS = net.minecraft.client.gui.Font.class;

    public static int fontWidth(Object font, String text) {
        Method m = findMethod(FONT_CLASS, "m_92895_", "width", String.class);
        if (m == null || font == null) return text.length() * 6;
        try { return (int) m.invoke(font, text); } catch (Throwable t) { return text.length() * 6; }
    }

    // ==================== Screen ====================
    private static final Class<?> SCREEN_CLASS = net.minecraft.client.gui.screens.Screen.class;
    private static final Class<?> GUI_EVENT_LISTENER_CLASS =
            net.minecraft.client.gui.components.events.GuiEventListener.class;

    public static void screenAddRenderableWidget(Object screen, Object widget) {
        Method m = findMethod(SCREEN_CLASS, "m_142416_", "addRenderableWidget", GUI_EVENT_LISTENER_CLASS);
        if (m != null) {
            try { m.invoke(screen, widget); } catch (Throwable t) {
                LOG.warn("[Mine-Terminal] addRenderableWidget failed: {}", t.getMessage());
            }
        }
    }

    public static void screenOnClose(Object screen) {
        Method m = findMethod(SCREEN_CLASS, "m_7379_", "onClose");
        if (m != null) {
            try { m.invoke(screen); } catch (Throwable t) {}
        }
    }

    // ==================== Button ====================
    private static final Class<?> BUTTON_CLASS = net.minecraft.client.gui.components.Button.class;

    public static Object buttonBuilder(Object label, Object onPress) {
        Class<?> onPressClass = null;
        try {
            onPressClass = Class.forName("net.minecraft.client.gui.components.Button$OnPress");
        } catch (Throwable ignore) {}

        Method m = null;
        if (onPressClass != null) {
            m = findMethod(BUTTON_CLASS, "m_253074_", "builder",
                    net.minecraft.network.chat.Component.class, onPressClass);
        }
        if (m == null) {
            // 退化搜索：找静态方法 builder
            for (Method meth : BUTTON_CLASS.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(meth.getModifiers())
                        && meth.getParameterCount() == 2
                        && (meth.getName().equals("builder") || meth.getName().equals("m_253074_"))) {
                    meth.setAccessible(true);
                    m = meth;
                    break;
                }
            }
        }
        if (m == null) return null;
        try { return m.invoke(null, label, onPress); } catch (Throwable t) { return null; }
    }

    public static Object buttonBuilderBounds(Object builder, int x, int y, int w, int h) {
        Class<?> builderClass = builder.getClass();
        Method m = findMethod(builderClass, "m_252987_", "bounds",
                int.class, int.class, int.class, int.class);
        if (m == null) {
            try { m = builderClass.getMethod("bounds", int.class, int.class, int.class, int.class); }
            catch (Throwable ignore) {}
        }
        if (m == null) return builder;
        try { return m.invoke(builder, x, y, w, h); } catch (Throwable t) { return builder; }
    }

    public static Object buttonBuilderBuild(Object builder) {
        Class<?> builderClass = builder.getClass();
        Method m = findMethod(builderClass, "m_253136_", "build");
        if (m == null) {
            try { m = builderClass.getMethod("build"); } catch (Throwable ignore) {}
        }
        if (m == null) return null;
        try { return m.invoke(builder); } catch (Throwable t) { return null; }
    }

    // ==================== GuiGraphics ====================
    private static final Class<?> GG_CLASS = net.minecraft.client.gui.GuiGraphics.class;

    public static void ggFill(Object gg, int x1, int y1, int x2, int y2, int color) {
        Method m = findMethod(GG_CLASS, "m_280509_", "fill",
                int.class, int.class, int.class, int.class, int.class);
        if (m != null) {
            try { m.invoke(gg, x1, y1, x2, y2, color); } catch (Throwable t) {}
        }
    }

    public static void ggDrawString(Object gg, Object font, String text, int x, int y, int color, boolean shadow) {
        // 6 参数版本：drawString(Font, String, int, int, int, boolean) — m_280056_
        Method m = findMethod(GG_CLASS, "m_280056_", "drawString",
                FONT_CLASS, String.class, int.class, int.class, int.class, boolean.class);
        if (m == null) return;
        try { m.invoke(gg, font, text, x, y, color, shadow); } catch (Throwable t) {
            LOG.debug("[Mine-Terminal] drawString failed: {}", t.getMessage());
        }
    }

    public static void ggDrawCenteredString(Object gg, Object font, String text, int x, int y, int color) {
        // 5 参数版本：drawCenteredString(Font, String, int, int, int) — m_280137_
        Method m = findMethod(GG_CLASS, "m_280137_", "drawCenteredString",
                FONT_CLASS, String.class, int.class, int.class, int.class);
        if (m == null) return;
        try { m.invoke(gg, font, text, x, y, color); } catch (Throwable t) {}
    }

    // ==================== KeyboardHandler ====================
    private static final Class<?> KB_CLASS = net.minecraft.client.KeyboardHandler.class;

    public static void setClipboard(String text) {
        MinecraftHolder mc = getMinecraft();
        if (mc == null) return;
        Object kb = mc.getKeyboard_handler();
        if (kb == null) return;
        Method m = findMethod(KB_CLASS, "m_90911_", "setClipboard", String.class);
        if (m != null) {
            try { m.invoke(kb, text); } catch (Throwable t) {}
        }
    }

    public static String getClipboard() {
        MinecraftHolder mc = getMinecraft();
        if (mc == null) return "";
        Object kb = mc.getKeyboard_handler();
        if (kb == null) return "";
        Method m = findMethod(KB_CLASS, "m_90876_", "getClipboard");
        if (m == null) return "";
        try { return (String) m.invoke(kb); } catch (Throwable t) { return ""; }
    }

    // ==================== CommandSourceStack ====================
    private static final Class<?> CSS_CLASS = net.minecraft.commands.CommandSourceStack.class;

    public static void cssSendSuccess(Object css, java.util.function.Supplier<Component> supplier, boolean sendToAdmins) {
        Method m = findMethod(CSS_CLASS, "m_288197_", "sendSuccess",
                java.util.function.Supplier.class, boolean.class);
        if (m != null) {
            try { m.invoke(css, supplier, sendToAdmins); } catch (Throwable t) {}
        }
    }

    public static void cssSendFailure(Object css, Component message) {
        Method m = findMethod(CSS_CLASS, "m_81352_", "sendFailure",
                net.minecraft.network.chat.Component.class);
        if (m != null) {
            try { m.invoke(css, message); } catch (Throwable t) {}
        }
    }

    // ==================== 工具方法 ====================

    private static Method findMethod(Class<?> clazz, String srgName, String deobfName, Class<?>... args) {
        String key = cacheKey(clazz, srgName, deobfName, args);
        Method cached = methodCache.get(key);
        if (cached != null) return cached;

        if (srgName != null) {
            try {
                Method m = ObfuscationReflectionHelper.findMethod(clazz, srgName, args);
                if (m != null) {
                    methodCache.put(key, m);
                    return m;
                }
            } catch (Throwable ignore) {}
        }
        if (deobfName != null) {
            try {
                Method m = clazz.getMethod(deobfName, args);
                methodCache.put(key, m);
                return m;
            } catch (Throwable ignore) {}
        }
        return null;
    }

    private static Object getFieldValue(Object obj, Class<?> clazz, String srgName, String deobfName) {
        if (obj == null) return null;
        String key = "F:" + clazz.getName() + ":" + srgName + ":" + deobfName;
        Field cached = fieldCache.get(key);
        if (cached == null) {
            try { cached = ObfuscationReflectionHelper.findField(clazz, srgName); }
            catch (Throwable ignore) {
                try { cached = clazz.getField(deobfName); } catch (Throwable ignore2) {}
            }
            if (cached != null) {
                cached.setAccessible(true);
                fieldCache.put(key, cached);
            }
        }
        if (cached == null) return null;
        try { return cached.get(obj); } catch (Throwable t) { return null; }
    }

    private static String cacheKey(Class<?> clazz, String srgName, String deobfName, Class<?>... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append('|').append(srgName).append('|').append(deobfName);
        for (Class<?> a : args) sb.append('|').append(a == null ? "?" : a.getName());
        return sb.toString();
    }
}
