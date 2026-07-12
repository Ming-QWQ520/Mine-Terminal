import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;

/**
 * 只修补 ClientTerminalManager：在 initialize() 末尾添加 startMonitor() 调用。
 * 不碰 TerminalScreen（避免 VerifyError）。
 *
 * 同时把 PtyTerminalSession 的 setConsole(false) 改为 setConsole(true)。
 */
public class PatchV2 {

    public static void main(String[] args) throws Exception {
        String baseDir = args[0];
        String outDir = args[1];

        // 1. 修补 ClientTerminalManager — 添加 startMonitor 调用
        patchCTM(baseDir, outDir);

        // 2. 修补 PtyTerminalSession — setConsole(true)
        patchPtySession(baseDir, outDir);

        System.out.println("All patches applied successfully.");
    }

    static void patchCTM(String baseDir, String outDir) throws Exception {
        Path inPath = Paths.get(baseDir, "com/mineterm/client/ClientTerminalManager.class");
        Path outPath = Paths.get(outDir, "com/mineterm/client/ClientTerminalManager.class");

        ClassReader cr = new ClassReader(Files.readAllBytes(inPath));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("initialize") && method.desc.equals("()V")) {
                System.out.println("Patching initialize() in ClientTerminalManager");
                for (int i = method.instructions.size() - 1; i >= 0; i--) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(insn,
                            new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "com/mineterm/client/terminal/AsyncSessionCreator",
                                "startMonitor", "()V"));
                        System.out.println("  Inserted startMonitor() before return");
                        break;
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, cw.toByteArray());
        System.out.println("Written: " + outPath);
    }

    static void patchPtySession(String baseDir, String outDir) throws Exception {
        Path inPath = Paths.get(baseDir, "com/mineterm/client/terminal/PtyTerminalSession.class");
        Path outPath = Paths.get(outDir, "com/mineterm/client/terminal/PtyTerminalSession.class");

        ClassReader cr = new ClassReader(Files.readAllBytes(inPath));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("start")) {
                System.out.println("Patching start() in PtyTerminalSession");
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.ICONST_0) {
                        // 检查下一条是否是 invokevirtual setConsole
                        AbstractInsnNode next = method.instructions.get(i + 1);
                        if (next != null && next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode minsn = (MethodInsnNode) next;
                            if (minsn.name.equals("setConsole")) {
                                System.out.println("  Found setConsole(false), changing to true");
                                method.instructions.set(insn, new InsnNode(Opcodes.ICONST_1));
                                break;
                            }
                        }
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, cw.toByteArray());
        System.out.println("Written: " + outPath);
    }
}
