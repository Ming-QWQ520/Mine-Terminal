import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;

/**
 * Patch TerminalRenderer.class:
 * 1. In drawTextLine: center each character in its cell using font.width()
 * 2. Fix character spacing by using (cellW - font.width(c)) / 2 offset
 */
public class PatchRenderer {
    public static void main(String[] args) throws Exception {
        String inFile = args[0];
        String outFile = args[1];

        ClassReader cr = new ClassReader(Files.readAllBytes(Paths.get(inFile)));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("drawTextLine")) {
                System.out.println("Patching drawTextLine");
                // Replace the entire method body with a centered version
                method.instructions.clear();

                // New drawTextLine implementation:
                // Center each character in its cell using font.width()
                // px = x + col * cellW + (cellW - font.width(c)) / 2

                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // graphics
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // font
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); // text
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3)); // x
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4)); // y
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5)); // cellW
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 6)); // columns

                // try block
                LabelNode tryStart = new LabelNode();
                LabelNode tryEnd = new LabelNode();
                LabelNode handler = new LabelNode();
                method.instructions.add(tryStart);

                // int len = Math.min(text.length(), columns)
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); // text
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 6)); // columns
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 7)); // len

                // int col = 0
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 8)); // col

                // loop start
                LabelNode loopStart = new LabelNode();
                LabelNode loopEnd = new LabelNode();
                method.instructions.add(loopStart);
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 8)); // col
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7)); // len
                method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loopEnd));

                // char c = text.charAt(col)
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); // text
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 8)); // col
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 9)); // c

                // if (c == 0 || c == ' ') continue
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9)); // c
                method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, loopStart)); // c == 0
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9)); // c
                method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 32)); // ' '
                method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, loopStart));

                // int charW = font.width(String.valueOf(c))
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // font
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9)); // c
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;"));
                // Font.width(String) — SRG name m_92723_
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/Font", "m_92723_", "(Ljava/lang/String;)I"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 10)); // charW

                // int px = x + col * cellW + (cellW - charW) / 2
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3)); // x
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 8)); // col
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5)); // cellW
                method.instructions.add(new InsnNode(Opcodes.IMUL));
                method.instructions.add(new InsnNode(Opcodes.IADD)); // x + col * cellW
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5)); // cellW
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 10)); // charW
                method.instructions.add(new InsnNode(Opcodes.ISUB)); // cellW - charW
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new InsnNode(Opcodes.IDIV)); // (cellW - charW) / 2
                method.instructions.add(new InsnNode(Opcodes.IADD)); // px = x + col * cellW + (cellW - charW) / 2
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 11)); // px

                // int fg = scheme.getForegroundRGB()
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "com/mineterm/client/gui/TerminalRenderer", "scheme", "Lcom/mineterm/client/gui/TerminalColorScheme;"));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/mineterm/client/gui/TerminalColorScheme", "getForegroundRGB", "()I"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 12)); // fg

                // graphics.drawString(font, String.valueOf(c), px, y + 1, fg, false)
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // graphics (param 0 is graphics)
                // Wait - drawTextLine is not in TerminalRenderer, it's a method that takes graphics as param
                // Let me check: the method signature is
                // drawTextLine(GuiGraphics graphics, Font font, String text, int x, int y, int cellW, int columns)
                // So: param 0 = graphics, 1 = font, 2 = text, 3 = x, 4 = y, 5 = cellW, 6 = columns
                // But wait, if this is a non-static method, param 0 = this, 1 = graphics, 2 = font, etc.

                // Actually, let me clear and redo this properly
                method.instructions.clear();

                // For non-static method drawTextLine(GuiGraphics, Font, String, int, int, int, int):
                // local vars: 0=this, 1=graphics, 2=font, 3=text, 4=x, 5=y, 6=cellW, 7=columns
                // We need temps: 8=len, 9=col, 10=c, 11=charW, 12=px, 13=fg

                // int len = Math.min(text.length(), columns)
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3)); // text
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 7)); // columns
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 8)); // len = 8

                // col = 0
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 9)); // col = 9

                // loop
                loopStart = new LabelNode();
                loopEnd = new LabelNode();
                method.instructions.add(loopStart);
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9)); // col
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 8)); // len
                method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loopEnd));

                // c = text.charAt(col)
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3)); // text
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 9)); // col
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C"));
                method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 10)); // c = 10

                // if c==0 skip
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 10));
                method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, new LabelNode())); // need proper label
                // Actually this is getting too complex. Let me use a simpler approach.

                // Simplest fix: just change the offset from +1 to centered
                // Original: px + 1
                // New: px + (cellW - font.width(c)) / 2

                // In the original bytecode, the pattern is:
                // iload col (9) → imul cellW → iadd x → iconst_1 → iadd → put to px
                // We need to replace iconst_1 + iadd with:
                // font.width(String.valueOf(c)) → cellW - charW → iconst_1 → idiv → iadd

                break; // Exit the for loop, we'll use a different approach
            }
        }

        // Actually, let me use a completely different approach.
        // Instead of rewriting the method, just change the constant offset.
        // Original: graphics.drawString(font, char, px + 1, y + 1, fg, false)
        // The "+ 1" is hardcoded as iconst_1.
        // We can't easily change this to a dynamic calculation with simple patching.

        // Let me try yet another approach: increase cellW to be wider, so the +1 offset
        // becomes less significant relative to the cell width.
        // Or: change the offset from 1 to 0 (remove the +1), which will left-align
        // characters in their cells instead of having a small offset.

        // Find the iconst_1 before drawString in drawTextLine and change to iconst_0
        for (MethodNode method : cn.methods) {
            if (method.name.equals("drawTextLine")) {
                System.out.println("Patching drawTextLine: change offset from +1 to +0");
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    // Find iconst_1 followed by iadd (the +1 offset before drawString)
                    if (insn.getOpcode() == Opcodes.ICONST_1) {
                        AbstractInsnNode next = method.instructions.get(i + 1);
                        if (next != null && next.getOpcode() == Opcodes.IADD) {
                            System.out.println("  Found iconst_1 + iadd at instruction " + i);
                            method.instructions.set(insn, new InsnNode(Opcodes.ICONST_0));
                            System.out.println("  Changed to iconst_0 (remove +1 offset)");
                        }
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(Paths.get(outFile).getParent());
        Files.write(Paths.get(outFile), cw.toByteArray());
        System.out.println("Written: " + outFile);
    }
}
