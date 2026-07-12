import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;

/**
 * 用 ASM 库正确修改 TerminalScreen.class：
 * 1. 在 init() 方法中，将 createSession 调用替换为跳过（用 aconst_null 代替返回值）
 * 2. ASM 会自动重新计算 StackMapTable，避免 VerifyError
 *
 * 同时修改 ClientTerminalManager.initialize() 在末尾添加
 * AsyncSessionCreator.startMonitor() 调用。
 */
public class PatchClasses {

    public static void main(String[] args) throws Exception {
        String baseDir = args[0];
        String outDir = args[1];

        // 1. 修补 TerminalScreen.class — 跳过 init() 中的 createSession
        patchTerminalScreen(baseDir, outDir);

        // 2. 修补 ClientTerminalManager.class — 添加 startMonitor 调用
        patchClientTerminalManager(baseDir, outDir);

        System.out.println("All patches applied successfully.");
    }

    static void patchTerminalScreen(String baseDir, String outDir) throws Exception {
        Path inPath = Paths.get(baseDir, "com/mineterm/client/gui/TerminalScreen.class");
        Path outPath = Paths.get(outDir, "com/mineterm/client/gui/TerminalScreen.class");

        ClassReader cr = new ClassReader(Files.readAllBytes(inPath));
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("m_7856_") || method.name.equals("init")) {
                System.out.println("Patching method: " + method.name);
                // 找到 createSession 的 invokevirtual 调用并替换为 aconst_null + nop + nop
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;
                        if (minsn.name.equals("createSession") &&
                            minsn.owner.equals("com/mineterm/client/terminal/TerminalSessionManager")) {
                            System.out.println("  Found createSession call at instruction " + i);
                            // 替换为: aconst_null, nop, nop (3 字节，与 invokevirtual 相同)
                            method.instructions.set(insn, new InsnNode(Opcodes.ACONST_NULL));
                            method.instructions.insert(insn, new InsnNode(Opcodes.NOP));
                            method.instructions.insert(insn.getNext(), new InsnNode(Opcodes.NOP));
                            System.out.println("  Replaced with aconst_null + nop + nop");
                            break;
                        }
                    }
                }
            }
        }

        // 写入，让 ASM 重新计算 frames
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, cw.toByteArray());
        System.out.println("Written: " + outPath);
    }

    static void patchClientTerminalManager(String baseDir, String outDir) throws Exception {
        Path inPath = Paths.get(baseDir, "com/mineterm/client/ClientTerminalManager.class");
        Path outPath = Paths.get(outDir, "com/mineterm/client/ClientTerminalManager.class");

        ClassReader cr = new ClassReader(Files.readAllBytes(inPath));
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("initialize") && method.desc.equals("()V")) {
                System.out.println("Patching initialize() in ClientTerminalManager");
                // 在 return 指令前插入 invokestatic AsyncSessionCreator.startMonitor()
                for (int i = method.instructions.size() - 1; i >= 0; i--) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(insn,
                            new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "com/mineterm/client/terminal/AsyncSessionCreator",
                                "startMonitor", "()V"));
                        System.out.println("  Inserted AsyncSessionCreator.startMonitor() before return");
                        break;
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, cw.toByteArray());
        System.out.println("Written: " + outPath);
    }
}
