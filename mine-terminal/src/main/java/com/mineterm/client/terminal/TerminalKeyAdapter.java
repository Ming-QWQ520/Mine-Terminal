package com.mineterm.client.terminal;

import com.mineterm.MineTerminal;
import com.mineterm.client.util.OSUtil;
import com.mineterm.common.MineTerminalConfig;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 键盘适配器：将 Minecraft 的 GLFW 键盘事件转换为终端输入字节序列。
 *
 * ★ 重要：直接写入进程 stdin，不经过 jediterm 的 TerminalOutputStream ★
 *
 * Windows 上用系统默认编码（GBK），Linux/Mac 上用 UTF-8。
 * Backspace 发送 0x08（BS），不是 0x7F（DEL）。
 */
public final class TerminalKeyAdapter {

    private TerminalKeyAdapter() {}

    private static final Charset OUTPUT_CHARSET = Charset.defaultCharset();

    /**
     * 处理 keyPressed 事件。返回 true 表示已消费。
     */
    public static boolean handleKeyPressed(TerminalSession session,
                                           int keyCode, int scanCode, int modifiers) {
        if (session == null || !session.isAlive()) return false;

        boolean ctrl  = (modifiers & GLFW.GLFW_MOD_CONTROL)  != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT)    != 0;
        boolean alt   = (modifiers & GLFW.GLFW_MOD_ALT)      != 0;

        // Ctrl+Shift+C / V: 复制/粘贴
        if (ctrl && shift) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                copyToClipboard(session);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                pasteFromClipboard(session);
                return true;
            }
        }

        // Ctrl 组合键
        if (ctrl && !shift && !alt) {
            byte[] seq = ctrlCombo(keyCode);
            if (seq != null) {
                sendToProcess(session, seq);
                return true;
            }
        }

        // 特殊键
        byte[] seq = specialKeySequence(keyCode, modifiers);
        if (seq != null) {
            sendToProcess(session, seq);
            return true;
        }

        // Alt+key → ESC + key
        if (alt) {
            sendToProcess(session, new byte[]{0x1B});
        }

        return false;
    }

    /**
     * 处理 charTyped 事件（字符输入）。
     * 用系统默认编码发送，确保中文正确。
     */
    public static boolean handleCharTyped(TerminalSession session, char c, int modifiers) {
        if (session == null || !session.isAlive()) return false;
        if (c == 0) return false;
        // 控制字符已由 keyPressed 处理
        if (c < 0x20 && c != '\t' && c != '\r' && c != '\n') return false;

        // 用系统默认编码（Windows=GBK, Linux=UTF-8）
        byte[] bytes = String.valueOf(c).getBytes(OUTPUT_CHARSET);
        sendToProcess(session, bytes);
        return true;
    }

    /**
     * 直接写入进程 stdin。
     * Windows 上把 DEL (0x7F) 转为 BS (0x08)。
     */
    private static void sendToProcess(TerminalSession session, byte[] data) {
        try {
            if (OSUtil.isWindows()) {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == 0x7F) data[i] = 0x08;
                }
            }
            session.write(data);
        } catch (Throwable t) {
            MineTerminal.LOGGER.warn("[Mine-Terminal] Failed to send input: {}", t.getMessage());
        }
    }

    private static byte[] ctrlCombo(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            int c = keyCode - GLFW.GLFW_KEY_A + 1;
            return new byte[]{(byte) c};
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_BRACKET:  return new byte[]{0x1B};
            case GLFW.GLFW_KEY_BACKSLASH:     return new byte[]{0x1C};
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return new byte[]{0x1D};
            case GLFW.GLFW_KEY_ENTER:         return new byte[]{0x0A};
        }
        return null;
    }

    private static byte[] specialKeySequence(int keyCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER:
                // Windows 上用 \r\n，Linux/Mac 上用 \n
                return OSUtil.isWindows() ? new byte[]{0x0D, 0x0A} : new byte[]{0x0D};
            case GLFW.GLFW_KEY_BACKSPACE:
                return new byte[]{0x08};
            case GLFW.GLFW_KEY_TAB:
                return new byte[]{0x09};
            case GLFW.GLFW_KEY_ESCAPE:
                return new byte[]{0x1B};
            case GLFW.GLFW_KEY_UP:
                return escSeq("[A");
            case GLFW.GLFW_KEY_DOWN:
                return escSeq("[B");
            case GLFW.GLFW_KEY_RIGHT:
                return escSeq("[C");
            case GLFW.GLFW_KEY_LEFT:
                return escSeq("[D");
            case GLFW.GLFW_KEY_HOME:
                return escSeq("[H");
            case GLFW.GLFW_KEY_END:
                return escSeq("[F");
            case GLFW.GLFW_KEY_PAGE_UP:
                return escSeq("[5~");
            case GLFW.GLFW_KEY_PAGE_DOWN:
                return escSeq("[6~");
            case GLFW.GLFW_KEY_INSERT:
                return escSeq("[2~");
            case GLFW.GLFW_KEY_DELETE:
                return escSeq("[3~");
            case GLFW.GLFW_KEY_F1:  return escSeq("OP");
            case GLFW.GLFW_KEY_F2:  return escSeq("OQ");
            case GLFW.GLFW_KEY_F3:  return escSeq("OR");
            case GLFW.GLFW_KEY_F4:  return escSeq("OS");
            case GLFW.GLFW_KEY_F5:  return escSeq("[15~");
            case GLFW.GLFW_KEY_F6:  return escSeq("[17~");
            case GLFW.GLFW_KEY_F7:  return escSeq("[18~");
            case GLFW.GLFW_KEY_F8:  return escSeq("[19~");
            case GLFW.GLFW_KEY_F9:  return escSeq("[20~");
            case GLFW.GLFW_KEY_F10: return escSeq("[21~");
            case GLFW.GLFW_KEY_F11: return escSeq("[23~");
            case GLFW.GLFW_KEY_F12: return escSeq("[24~");
        }
        return null;
    }

    private static byte[] escSeq(String suffix) {
        byte[] s = suffix.getBytes(StandardCharsets.US_ASCII);
        byte[] r = new byte[s.length + 1];
        r[0] = 0x1B;
        System.arraycopy(s, 0, r, 1, s.length);
        return r;
    }

    private static void copyToClipboard(TerminalSession session) {
        // 简化：暂不实现复制
    }

    private static void pasteFromClipboard(TerminalSession session) {
        try {
            String text = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
            if (text == null || text.isEmpty()) return;
            text = text.replace("\r\n", "\r").replace("\n", "\r");
            byte[] bytes = text.getBytes(OUTPUT_CHARSET);
            sendToProcess(session, bytes);
        } catch (Throwable t) {
            // ignore
        }
    }
}
