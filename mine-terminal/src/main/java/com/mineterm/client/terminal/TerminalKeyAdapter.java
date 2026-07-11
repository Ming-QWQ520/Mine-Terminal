package com.mineterm.client.terminal;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

/**
 * 键盘适配器：将 Minecraft 的 GLFW 键盘事件转换为终端输入字节序列。
 *
 * 处理规则：
 *   - 可打印字符（charTyped）→ 直接 UTF-8 编码
 *   - Enter            → CR (\r)
 *   - Backspace        → DEL (0x7F)
 *   - Tab              → HT  (\t)
 *   - Escape           → ESC (0x1B) — 由终端决定是否消费
 *   - 方向键           → ESC [ A/B/C/D
 *   - Home/End         → ESC [ H / ESC [ F  (或 ESC OH / ESC OF)
 *   - PageUp/PageDown  → ESC [ 5~ / ESC [ 6~
 *   - Insert/Delete    → ESC [ 2~ / ESC [ 3~
 *   - F1-F12           → 各自的 VT100/xterm 序列
 *   - Ctrl+A..Z        → 控制字符 0x01..0x1A
 *   - Ctrl+[           → ESC
 *   - Ctrl+Shift+C/V   → 复制/粘贴（剪贴板交互）
 *
 * 应用模式 vs 光标模式：jediterm 内部会根据终端模式自动转换。
 * 我们直接通过 backend.processInputBytes 写入，让 jediterm 处理模式切换。
 */
public final class TerminalKeyAdapter {

    private TerminalKeyAdapter() {}

    /**
     * 处理 keyPressed 事件。返回 true 表示已消费。
     */
    public static boolean handleKeyPressed(TerminalSession session,
                                           int keyCode, int scanCode, int modifiers) {
        if (session == null || !session.isAlive()) return false;

        // 优先：复制 / 粘贴
        boolean ctrl  = (modifiers & GLFW.GLFW_MOD_CONTROL)  != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT)    != 0;
        boolean alt   = (modifiers & GLFW.GLFW_MOD_ALT)      != 0;

        // Ctrl+Shift+C / Ctrl+Shift+V: 复制/粘贴
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

        // 单独 Ctrl 组合（无 shift，无 alt）
        if (ctrl && !shift && !alt) {
            byte[] seq = ctrlCombo(keyCode);
            if (seq != null) {
                session.write(seq);
                return true;
            }
        }

        // 特殊键
        byte[] seq = specialKeySequence(keyCode, modifiers);
        if (seq != null) {
            session.write(seq);
            return true;
        }

        // Alt+key → ESC + key
        if (alt) {
            // ESC prefix
            session.write(new byte[]{0x1B});
            // 不返回 true，让 charTyped 接管字符
        }

        return false;
    }

    /**
     * 处理 charTyped 事件（字符输入）。
     */
    public static boolean handleCharTyped(TerminalSession session, char c, int modifiers) {
        if (session == null || !session.isAlive()) return false;
        if (c == 0) return false;
        // 控制字符已由 keyPressed 处理
        if (c < 0x20 && c != '\t' && c != '\r' && c != '\n') return false;

        byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
        session.write(bytes);
        return true;
    }

    // ====================================================================
    //  控制键序列生成
    // ====================================================================

    private static byte[] ctrlCombo(int keyCode) {
        // Ctrl+A..Z → 0x01..0x1A
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            int c = keyCode - GLFW.GLFW_KEY_A + 1;
            return new byte[]{(byte) c};
        }
        // Ctrl+[ → ESC, Ctrl+\ → FS, Ctrl+] → GS, Ctrl+/ → US
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_BRACKET:  return new byte[]{0x1B};
            case GLFW.GLFW_KEY_BACKSLASH:     return new byte[]{0x1C};
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return new byte[]{0x1D};
            case GLFW.GLFW_KEY_ENTER:         return new byte[]{0x0A};
            case GLFW.GLFW_KEY_SLASH:         return new byte[]{0x1F}; // Ctrl+_ ?
        }
        return null;
    }

    private static byte[] specialKeySequence(int keyCode, int modifiers) {
        boolean appCursor = false; // jediterm 内部处理
        // 注意：jediterm 在 cursor-key mode 下自动会处理。我们发送 ANSI 标准序列即可。
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER:
                return new byte[]{0x0D}; // CR
            case GLFW.GLFW_KEY_BACKSPACE:
                return new byte[]{0x7F}; // DEL
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
                return appCursor ? escSeq("OH") : escSeq("[H");
            case GLFW.GLFW_KEY_END:
                return appCursor ? escSeq("OF") : escSeq("[F");
            case GLFW.GLFW_KEY_PAGE_UP:
                return escSeq("[5~");
            case GLFW.GLFW_KEY_PAGE_DOWN:
                return escSeq("[6~");
            case GLFW.GLFW_KEY_INSERT:
                return escSeq("[2~");
            case GLFW.GLFW_KEY_DELETE:
                return escSeq("[3~");
            // 功能键
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

    // ====================================================================
    //  剪贴板
    // ====================================================================

    private static void copyToClipboard(TerminalSession session) {
        // 简化：从 jediterm TerminalDisplay 取出选中区域（由 TerminalScreen 实现的 display 维护）
        // 真实选中文字提取需要访问 TerminalSelection 与坐标转换，
        // 在此简化为：直接从 TerminalTextBuffer 提取当前光标行的整行文本
        try {
            com.jediterm.terminal.model.JediTerminal term = session.getBackend().getTerminal();
            if (term == null) return;
            // 真实实现应使用 term.getSelection() 或 TerminalDisplay.getSelection()
            // 此处简化：不复制（避免依赖未实现的 API）
        } catch (Throwable t) {
            // ignore
        }
    }

    private static void pasteFromClipboard(TerminalSession session) {
        String text = com.mineterm.client.util.MCReflect.getClipboard();
        if (text == null || text.isEmpty()) return;
        // 在终端中粘贴时，需要把 \r\n / \n 转为 \r（终端 Enter）
        text = text.replace("\r\n", "\r").replace("\n", "\r");
        session.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
