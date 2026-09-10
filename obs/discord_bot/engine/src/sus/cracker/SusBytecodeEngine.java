package sus.cracker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║       SuS Cracker - ADVANCED Bytecode Suite v4.1                    ║
 * ║   Obfuscator | Scanner | Cracker(43) | Deobfuscator | DeobfSrc      ║
 * ║   Targets: Meteor Addon | Fabric Mod | Client | Forge | Any         ║
 * ║            Made by SuS Cracker Team | Discord Bot                   ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
public class SusBytecodeEngine {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar SusBytecodeEngine.jar <mode> <input.jar> [output.jar] [extra] [options]");
            System.err.println("Modes: obfuscate | scan | crack | deobfuscate | clean");
            System.exit(1);
        }
        String mode = args[0].toLowerCase();
        File inputJar = new File(args[1]);
        if (!inputJar.exists()) {
            System.err.println("Error: Input file does not exist: " + inputJar.getAbsolutePath());
            System.exit(1);
        }
        try {
            switch (mode) {
                case "obfuscate": {
                    File obfOut = args.length > 2 ? new File(args[2]) : new File("obfuscated_" + inputJar.getName());
                    String preset = args.length > 3 ? args[3].toLowerCase() : "aggressive";
                    boolean rename = args.length <= 4 || !"false".equalsIgnoreCase(args[4]);
                    runObfuscator(inputJar, obfOut, preset, rename);
                    break;
                }
                case "scan":
                    runScanner(inputJar);
                    break;
                case "crack": {
                    File crackOut = args.length > 2 ? new File(args[2]) : new File("cracked_" + inputJar.getName());
                    String crackerTarget = args.length > 3 ? args[3].toLowerCase() : "any";
                    runCracker(inputJar, crackOut, crackerTarget);
                    break;
                }
                case "deobfuscate": {
                    File deobOut = args.length > 2 ? new File(args[2]) : new File("deobfuscated_" + inputJar.getName());
                    File srcOut  = args.length > 3 ? new File(args[3]) : new File("secure_source_code.zip");
                    String decompiler = args.length > 4 ? args[4].toLowerCase() : "auto";
                    runDeobfuscator(inputJar, deobOut, srcOut, decompiler);
                    break;
                }
                case "clean":
                case "clear": {
                    File cleanOutput = args.length > 2 ? new File(args[2]) : new File("cleaned_" + inputJar.getName());
                    String honeypot = args.length > 3 ? args[3] : null;
                    runCleaner(inputJar, cleanOutput, honeypot);
                    break;
                }
                case "protect": {
                    File protOut = args.length > 2 ? new File(args[2]) : new File("protected_" + inputJar.getName());
                    String hwid = args.length > 3 ? args[3] : "NONE";
                    long expiry = args.length > 4 ? Long.parseLong(args[4]) : 0L;
                    String webhook = args.length > 5 ? args[5] : "NONE";
                    runProtector(inputJar, protOut, hwid, expiry, webhook);
                    break;
                }
                case "diff": {
                    File jar2 = args.length > 2 ? new File(args[2]) : null;
                    File diffOut = args.length > 3 ? new File(args[3]) : new File("diff_report.txt");
                    if (jar2 == null || !jar2.exists()) {
                        System.err.println("Error: Secondary JAR for diff does not exist.");
                        System.exit(1);
                    }
                    runDiffer(inputJar, jar2, diffOut);
                    break;
                }
                default:
                    System.err.println("Unknown mode: " + mode);
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Execution Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 1 ─ ADVANCED OBFUSCATOR (PRESETS: standard, aggressive, extreme)
    // ══════════════════════════════════════════════════════════════
    private static void runObfuscator(File inputJar, File outputJar, String preset, boolean allowRename) throws Exception {
        System.out.println("[*] [SuS Obfuscator v4.2] Processing " + inputJar.getName() + " [Preset: " + preset.toUpperCase() + "]...");
        Map<String, byte[]> jar = readJar(inputJar);
        Map<String, byte[]> out = new LinkedHashMap<>();

        boolean isExtreme = preset.equals("extreme");
        boolean isAggressive = preset.equals("aggressive") || isExtreme;

        // Phase 1: Build rename mappings (classes, packages, and members)
        RenameResult renameRes = allowRename ? buildRenameMap(jar, isExtreme) : new RenameResult(Collections.emptyMap(), Collections.emptyMap());
        Map<String, String> classRenames = renameRes.classMap;
        Map<String, String> packageRenames = renameRes.packageMap;

        int classCount = 0, stringEnc = 0, numberObf = 0, flowObf = 0,
            poolPoll = 0, varObf = 0;

        Random rand = new Random();

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();

            if (!name.endsWith(".class")) {
                // Handle directories and non-class resources belonging to remapped packages
                String mappedName = name;
                for (Map.Entry<String, String> pEntry : packageRenames.entrySet()) {
                    String oldPkg = pEntry.getKey();
                    String newPkg = pEntry.getValue();
                    if (oldPkg.equals(newPkg)) continue;
                    if (name.equals(oldPkg)) {
                        mappedName = newPkg;
                        break;
                    } else if (name.startsWith(oldPkg)) {
                        mappedName = newPkg + name.substring(oldPkg.length());
                        break;
                    }
                }

                // Skip stale empty parent directories of remapped packages
                if (mappedName.endsWith("/") && !mappedName.equals("META-INF/") && !mappedName.startsWith("assets/") && !mappedName.startsWith("data/")) {
                    boolean hasContent = false;
                    for (String origKey : jar.keySet()) {
                        if (origKey.startsWith(name) && !origKey.equals(name)) {
                            String childMapped = origKey.endsWith(".class") ? classRenames.getOrDefault(origKey.replace(".class", ""), origKey) : origKey;
                            if (childMapped.startsWith(name)) {
                                hasContent = true;
                                break;
                            }
                        }
                    }
                    if (!hasContent && !packageRenames.containsValue(mappedName)) {
                        continue;
                    }
                }

                // Patch configuration files: fabric.mod.json, *.mixins.json, mods.toml, quilt.mod.json
                if (mappedName.equals("fabric.mod.json")) {
                    bytes = patchFabricModJson(bytes, classRenames);
                } else if (mappedName.endsWith(".mixins.json") || mappedName.endsWith("mixins.json")) {
                    bytes = patchMixinJson(bytes, classRenames, packageRenames);
                } else if (mappedName.equals("mods.toml") || mappedName.equals("quilt.mod.json")) {
                    bytes = patchGeneralConfig(bytes, classRenames);
                }

                out.put(mappedName, bytes);
                continue;
            }

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);

                boolean isMixin = cn.name.contains("/mixin/") || cn.name.endsWith("Mixin");
                boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;

                int classXorKey1 = 0x5A + (Math.abs(cn.name.hashCode()) % 150) + 7;
                int classXorKey2 = 0x3C + (Math.abs(cn.name.hashCode() >>> 3) % 110) + 13;
                String decryptMethodName = "\u200C\u200D\u200B_0x" + Integer.toHexString(Math.abs(cn.name.hashCode() ^ 0xCAFE) % 0xFFFF);
                boolean hasEncryptedStrings = false;

                // ─── 1.1 Multi-Round Polymorphic In-Class String Encryption ──
                if (!isMixin && !isInterface) {
                    for (MethodNode mn : cn.methods) {
                        for (AbstractInsnNode insn : mn.instructions.toArray()) {
                            if (insn instanceof LdcInsnNode) {
                                LdcInsnNode ldc = (LdcInsnNode) insn;
                                if (ldc.cst instanceof String) {
                                    String str = (String) ldc.cst;
                                    if (!str.isEmpty() && str.length() < 3000) {
                                        String enc = multiLayerEncryptString(str, classXorKey1, classXorKey2);
                                        InsnList il = new InsnList();
                                        il.add(new LdcInsnNode(enc));
                                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                            cn.name, decryptMethodName,
                                            "(Ljava/lang/String;)Ljava/lang/String;", false));
                                        mn.instructions.insertBefore(insn, il);
                                        mn.instructions.remove(insn);
                                        stringEnc++;
                                        hasEncryptedStrings = true;
                                    }
                                }
                            }
                        }
                    }

                    if (hasEncryptedStrings) {
                        cn.methods.add(buildRobustDecryptor(decryptMethodName, classXorKey1, classXorKey2));
                    }
                }

                // ─── 1.2 Multi-Step Constant & Arithmetic Obfuscation ────
                if (!isMixin) {
                    for (MethodNode mn : cn.methods) {
                        for (AbstractInsnNode insn : mn.instructions.toArray()) {
                            Integer constVal = getConstant(insn);
                            if (constVal != null && constVal != 0 && constVal != 1) {
                                int k1 = rand.nextInt(0x7FFF) + 19;
                                int k2 = rand.nextInt(0x7FFF) + 37;
                                int k3 = rand.nextInt(0xFF) + 7;
                                int enc = ((constVal ^ k1) + k2) ^ k3;

                                InsnList il = new InsnList();
                                il.add(new LdcInsnNode(enc));
                                il.add(new LdcInsnNode(k3));
                                il.add(new InsnNode(Opcodes.IXOR));
                                il.add(new LdcInsnNode(k2));
                                il.add(new InsnNode(Opcodes.ISUB));
                                il.add(new LdcInsnNode(k1));
                                il.add(new InsnNode(Opcodes.IXOR));

                                if (isAggressive) {
                                    // Add opaque mathematical identity: x = ((x << 1) - x)
                                    il.add(new InsnNode(Opcodes.DUP));
                                    il.add(new InsnNode(Opcodes.ICONST_1));
                                    il.add(new InsnNode(Opcodes.ISHL));
                                    il.add(new InsnNode(Opcodes.SWAP));
                                    il.add(new InsnNode(Opcodes.ISUB));
                                }

                                mn.instructions.insertBefore(insn, il);
                                mn.instructions.remove(insn);
                                numberObf++;
                            }
                        }
                    }
                }

                // ─── 1.3 Control Flow Flattening (Switch-State Machine) ───
                int flattenedMethods = 0;
                /*
                if (!isMixin && isAggressive) {
                    for (MethodNode mn : cn.methods) {
                        if ((mn.access & Opcodes.ACC_ABSTRACT) == 0
                                && (mn.access & Opcodes.ACC_NATIVE) == 0
                                && !mn.name.equals("<init>")
                                && !mn.name.equals("<clinit>")
                                && mn.instructions.size() >= 8
                                && mn.instructions.size() <= 120
                                && (mn.tryCatchBlocks == null || mn.tryCatchBlocks.isEmpty())) {
                            if (applyControlFlowFlattening(mn)) {
                                flattenedMethods++;
                            }
                        }
                    }
                }
                */

                // ─── 1.4 Mathematical Invariant Opaque Predicates & Anti-Decompiler Traps ─
                if (!isMixin && isAggressive) {
                    for (MethodNode mn : cn.methods) {
                        if ((mn.access & Opcodes.ACC_ABSTRACT) == 0
                            && (mn.access & Opcodes.ACC_NATIVE) == 0
                            && !mn.name.equals("<init>")
                            && !mn.name.equals("<clinit>")
                            && mn.instructions.size() > 6) {

                            LabelNode passLabel = new LabelNode();
                            InsnList guard = new InsnList();

                            // Mathematical Invariant: ((n * 2) | 1) != 0 is always true
                            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
                            guard.add(new InsnNode(Opcodes.L2I));
                            guard.add(new InsnNode(Opcodes.ICONST_1));
                            guard.add(new InsnNode(Opcodes.ISHL));
                            guard.add(new InsnNode(Opcodes.ICONST_1));
                            guard.add(new InsnNode(Opcodes.IOR));
                            guard.add(new JumpInsnNode(Opcodes.IFNE, passLabel));

                            // Bogus unreachable block to deceive static decompilers
                            guard.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
                            guard.add(new InsnNode(Opcodes.DUP));
                            guard.add(new LdcInsnNode("Security Integrity Violation"));
                            guard.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false));
                            guard.add(new InsnNode(Opcodes.ATHROW));
                            guard.add(passLabel);

                            // Anti-Decompiler Fake Exception Trap (Confuses CFG / AST graph analyzers)
                            LabelNode tryStart = new LabelNode();
                            LabelNode tryEnd = new LabelNode();
                            LabelNode handler = new LabelNode();
                            LabelNode trapPass = new LabelNode();

                            guard.add(tryStart);
                            guard.add(new JumpInsnNode(Opcodes.GOTO, trapPass));
                            guard.add(tryEnd);
                            guard.add(handler);
                            guard.add(new InsnNode(Opcodes.POP));
                            guard.add(new JumpInsnNode(Opcodes.GOTO, trapPass));
                            guard.add(trapPass);

                            mn.instructions.insert(guard);
                            if (mn.tryCatchBlocks == null) mn.tryCatchBlocks = new ArrayList<>();
                            mn.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, handler, "java/lang/Throwable"));

                            flowObf++;
                        }
                    }
                }

                // ─── 1.5 Complete Debug Stripper (No SourceFile / LineNumbers)
                cn.sourceFile = null;
                cn.sourceDebug = null;
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LineNumberNode) {
                            mn.instructions.remove(insn);
                        }
                    }
                }

                // ─── 1.6 Constant Pool Pollution ─────────────────────────
                if (!isMixin) {
                    String[] junkStrings = {
                        "\u200Bsus_cracker_protected", "\u200B\uFEFFauth_guard_v4",
                        "\u200C\u200Bsecured_by_sus", "\u200B\u200C\uFEFFobfuscated_v4",
                        "SuSCrackerSignature_v4.2", "ProtectedMethodInterceptor_x86_64",
                        "VMEntryDispatcherInternal"
                    };
                    for (MethodNode mn : cn.methods) {
                        if ((mn.access & Opcodes.ACC_ABSTRACT) == 0
                                && !mn.name.equals("<init>")
                                && !mn.name.equals("<clinit>")
                                && mn.instructions.size() > 0) {
                            String junk = junkStrings[Math.abs(mn.name.hashCode()) % junkStrings.length];
                            InsnList junkList = new InsnList();
                            LabelNode junkSkip = new LabelNode();
                            junkList.add(new InsnNode(Opcodes.ICONST_1));
                            junkList.add(new JumpInsnNode(Opcodes.IFNE, junkSkip));
                            junkList.add(new LdcInsnNode(junk));
                            junkList.add(new InsnNode(Opcodes.POP));
                            junkList.add(junkSkip);
                            AbstractInsnNode last = mn.instructions.getLast();
                            if (last != null) mn.instructions.insertBefore(last, junkList);
                            poolPoll++;
                        }
                    }
                }

                // ─── 1.7 Eradicate Local Variable & Parameter Tables ──────
                // Completely clear localVariables and parameters so decompilers cannot invent or keep 'var' names
                for (MethodNode mn : cn.methods) {
                    if (mn.localVariables != null) {
                        varObf += mn.localVariables.size();
                        mn.localVariables.clear();
                        mn.localVariables = null;
                    }
                    if (mn.parameters != null) {
                        mn.parameters.clear();
                        mn.parameters = null;
                    }
                }

                // ─── 1.8 Safe Remapping Pass ─────────────────────────────
                ClassNode obfCn = new ClassNode();
                if (!classRenames.isEmpty()) {
                    SimpleRemapper remapper = new SimpleRemapper(classRenames);
                    ClassRemapper crRemapper = new ClassRemapper(obfCn, remapper);
                    cn.accept(crRemapper);
                } else {
                    obfCn = cn;
                }

                ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
                obfCn.accept(cw);

                String newClassName = classRenames.getOrDefault(cn.name, cn.name) + ".class";
                out.put(newClassName, cw.toByteArray());
                classCount++;
            } catch (Exception ex) {
                System.err.println("[-] Error processing class " + name + ": " + ex);
                ex.printStackTrace();
                out.put(name, bytes);
            }
        }

        writeJar(outputJar, out);
        System.out.println("[+] [SuS Obfuscator] Completed successfully!");
        System.out.println("    Classes Processed   : " + classCount);
        System.out.println("    Strings Encrypted   : " + stringEnc);
        System.out.println("    Numbers Obfuscated  : " + numberObf);
        System.out.println("    Flow Invariants     : " + flowObf);
        System.out.println("    Pool Pollution      : " + poolPoll);
        System.out.println("    LocalVars Cleared   : " + varObf);
        System.out.println("    Output File         : " + outputJar.getAbsolutePath());
    }

    static class RenameResult {
        final Map<String, String> classMap;
        final Map<String, String> packageMap;

        RenameResult(Map<String, String> classMap, Map<String, String> packageMap) {
            this.classMap = classMap;
            this.packageMap = packageMap;
        }
    }

    private static RenameResult buildRenameMap(Map<String, byte[]> jar, boolean extreme) {
        Map<String, String> map = new HashMap<>();
        Map<String, String> packageMap = new HashMap<>();
        Set<String> excluded = new HashSet<>();

        // Phase 1.1: Short Clean Package Names (a/, b/, c/...) & Class Names (a/a.class, b/a.class...)
        List<String> sortedNames = new ArrayList<>(jar.keySet());
        Collections.sort(sortedNames); // Deterministic ordering

        int pkgCounter = 0;
        for (String name : sortedNames) {
            if (name.endsWith(".class")) {
                String cls = name.replace(".class", "");
                if (cls.startsWith("sus/cracker/runtime/") || cls.startsWith("org/spongepowered/")
                        || cls.startsWith("net/fabricmc/") || cls.startsWith("net/minecraft/") || cls.startsWith("com/mojang/")
                        || excluded.contains(cls)) {
                    continue;
                }
                if (cls.contains("/")) {
                    String origPkg = cls.substring(0, cls.lastIndexOf('/') + 1);
                    if (!packageMap.containsKey(origPkg)) {
                        String shortPkg = getShortName(pkgCounter++) + "/";
                        packageMap.put(origPkg, shortPkg);
                    }
                }
            }
        }

        Map<String, Integer> pkgClassCounters = new HashMap<>();
        for (String name : sortedNames) {
            if (name.endsWith(".class")) {
                String cls = name.replace(".class", "");
                if (cls.startsWith("sus/cracker/runtime/") || cls.startsWith("org/spongepowered/")
                        || cls.startsWith("net/fabricmc/") || cls.startsWith("net/minecraft/") || cls.startsWith("com/mojang/")
                        || excluded.contains(cls)) {
                    continue;
                }
                String targetPkg = "";
                if (cls.contains("/")) {
                    String origPkg = cls.substring(0, cls.lastIndexOf('/') + 1);
                    targetPkg = packageMap.getOrDefault(origPkg, origPkg);
                }
                int count = pkgClassCounters.getOrDefault(targetPkg, 0);
                String obfSimple = getShortName(count);
                pkgClassCounters.put(targetPkg, count + 1);
                map.put(cls, targetPkg + obfSimple);
            }
        }

        // Phase 1.2: ProGuard-style Private Field & Method Renaming (a, b, c...)
        for (Map.Entry<String, byte[]> entry : jar.entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith(".class")) continue;
            String cls = name.replace(".class", "");
            if (cls.startsWith("sus/cracker/runtime/") || cls.startsWith("org/spongepowered/")
                    || cls.startsWith("net/fabricmc/") || cls.startsWith("net/minecraft/") || cls.startsWith("com/mojang/")
                    || excluded.contains(cls) || cls.contains("/mixin/") || cls.endsWith("Mixin")) {
                continue;
            }

            try {
                ClassReader cr = new ClassReader(entry.getValue());
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                int fIdx = 0;
                for (FieldNode fn : cn.fields) {
                    if ((fn.access & Opcodes.ACC_PRIVATE) != 0) {
                        if (!fn.name.equals("serialVersionUID") && !fn.name.startsWith("$")) {
                            map.put(cls + "." + fn.name, getShortName(fIdx++));
                        }
                    }
                }

                int mIdx = 0;
                for (MethodNode mn : cn.methods) {
                    if ((mn.access & Opcodes.ACC_PRIVATE) != 0 && (mn.access & Opcodes.ACC_STATIC) == 0) {
                        if (!mn.name.equals("<init>") && !mn.name.equals("<clinit>")
                                && !mn.name.startsWith("$") && !mn.name.startsWith("\u200B")
                                && (mn.access & Opcodes.ACC_NATIVE) == 0) {
                            map.put(cls + "." + mn.name + mn.desc, getShortName(mIdx++));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return new RenameResult(map, packageMap);
    }

    private static String getShortName(int idx) {
        StringBuilder sb = new StringBuilder();
        idx++;
        while (idx > 0) {
            idx--;
            sb.append((char) ('a' + (idx % 26)));
            idx /= 26;
        }
        return sb.reverse().toString();
    }

    private static byte[] patchFabricModJson(byte[] originalBytes, Map<String, String> classRenames) {
        try {
            String content = new String(originalBytes, StandardCharsets.UTF_8);

            // Sort class renames by length descending to prevent partial prefix replacements
            List<Map.Entry<String, String>> sorted = new ArrayList<>(classRenames.entrySet());
            sorted.sort((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()));

            for (Map.Entry<String, String> entry : sorted) {
                String oldSlash = entry.getKey();
                String newSlash = entry.getValue();

                String oldDot = oldSlash.replace('/', '.');
                String newDot = newSlash.replace('/', '.');

                // Replace dot notation (standard in fabric.mod.json entrypoints)
                content = content.replace("\"" + oldDot + "\"", "\"" + newDot + "\"");
                content = content.replace("\"" + oldDot + "::", "\"" + newDot + "::");
                content = content.replace(" " + oldDot + "\"", " " + newDot + "\"");

                // Replace slash notation (if any)
                content = content.replace("\"" + oldSlash + "\"", "\"" + newSlash + "\"");
                content = content.replace("\"" + oldSlash + "::", "\"" + newSlash + "::");
            }

            return content.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[-] Warning: Failed to patch fabric.mod.json: " + e.getMessage());
            return originalBytes;
        }
    }

    private static byte[] patchMixinJson(byte[] originalBytes, Map<String, String> classRenames, Map<String, String> packageRenames) {
        try {
            String content = new String(originalBytes, StandardCharsets.UTF_8);

            // Find "package": "..."
            Matcher pkgMatcher = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
            if (pkgMatcher.find()) {
                String origDotPkg = pkgMatcher.group(1);
                String origSlashPkg = origDotPkg.replace('.', '/') + "/";

                String newSlashPkg = packageRenames.get(origSlashPkg);
                if (newSlashPkg != null) {
                    String newDotPkg = newSlashPkg.endsWith("/") ? newSlashPkg.substring(0, newSlashPkg.length() - 1) : newSlashPkg;
                    newDotPkg = newDotPkg.replace('/', '.');

                    content = content.substring(0, pkgMatcher.start(1)) + newDotPkg + content.substring(pkgMatcher.end(1));

                    // Now remap simple class names in mixin arrays
                    for (Map.Entry<String, String> entry : classRenames.entrySet()) {
                        String oldFull = entry.getKey();
                        String newFull = entry.getValue();
                        if (oldFull.startsWith(origSlashPkg)) {
                            String oldSimple = oldFull.substring(origSlashPkg.length());
                            String newSimple = newFull.startsWith(newSlashPkg) ? newFull.substring(newSlashPkg.length()) : newFull;
                            if (newSimple.contains("/")) {
                                newSimple = newSimple.substring(newSimple.lastIndexOf('/') + 1);
                            }
                            content = content.replace("\"" + oldSimple + "\"", "\"" + newSimple + "\"");
                        }
                    }
                }
            }

            return content.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[-] Warning: Failed to patch mixin json: " + e.getMessage());
            return originalBytes;
        }
    }

    private static byte[] patchGeneralConfig(byte[] originalBytes, Map<String, String> classRenames) {
        try {
            String content = new String(originalBytes, StandardCharsets.UTF_8);
            List<Map.Entry<String, String>> sorted = new ArrayList<>(classRenames.entrySet());
            sorted.sort((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()));

            for (Map.Entry<String, String> entry : sorted) {
                String oldDot = entry.getKey().replace('/', '.');
                String newDot = entry.getValue().replace('/', '.');
                content = content.replace("\"" + oldDot + "\"", "\"" + newDot + "\"");
                content = content.replace("'" + oldDot + "'", "'" + newDot + "'");
            }
            return content.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return originalBytes;
        }
    }

    private static boolean applyControlFlowFlattening(MethodNode mn) {
        try {
            InsnList insns = mn.instructions;
            if (insns.size() < 6) return false;

            List<InsnList> blocks = new ArrayList<>();
            InsnList cur = new InsnList();
            int total = insns.size();
            int threshold = Math.max(2, total / 3);

            for (AbstractInsnNode node : insns.toArray()) {
                insns.remove(node);
                cur.add(node);
                if (cur.size() >= threshold && blocks.size() < 2) {
                    blocks.add(cur);
                    cur = new InsnList();
                }
            }
            if (cur.size() > 0) {
                blocks.add(cur);
            }

            if (blocks.size() < 2) {
                for (InsnList b : blocks) insns.add(b);
                return false;
            }

            int stateVar = mn.maxLocals + 1;
            mn.maxLocals += 2;

            LabelNode loopStart = new LabelNode();
            LabelNode defaultLabel = new LabelNode();
            LabelNode[] labels = new LabelNode[blocks.size()];
            int[] keys = new int[blocks.size()];

            Random r = new Random(mn.name.hashCode());
            for (int i = 0; i < blocks.size(); i++) {
                labels[i] = new LabelNode();
                keys[i] = 0x1000 + (r.nextInt(0x5000) * 2) + i;
            }

            InsnList flat = new InsnList();
            flat.add(new LdcInsnNode(keys[0]));
            flat.add(new VarInsnNode(Opcodes.ISTORE, stateVar));

            flat.add(loopStart);
            flat.add(new VarInsnNode(Opcodes.ILOAD, stateVar));
            flat.add(new LookupSwitchInsnNode(defaultLabel, keys, labels));

            for (int i = 0; i < blocks.size(); i++) {
                flat.add(labels[i]);
                InsnList b = blocks.get(i);
                AbstractInsnNode last = b.getLast();
                boolean isReturnOrThrow = last != null && (last.getOpcode() >= Opcodes.IRETURN && last.getOpcode() <= Opcodes.RETURN || last.getOpcode() == Opcodes.ATHROW);

                if (i + 1 < blocks.size() && !isReturnOrThrow) {
                    b.add(new LdcInsnNode(keys[i + 1]));
                    b.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
                    b.add(new JumpInsnNode(Opcodes.GOTO, loopStart));
                }
                flat.add(b);
            }

            flat.add(defaultLabel);
            flat.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
            flat.add(new InsnNode(Opcodes.DUP));
            flat.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false));
            flat.add(new InsnNode(Opcodes.ATHROW));

            mn.instructions = flat;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String multiLayerEncryptString(String s, int key1, int key2) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        byte[] enc = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            int dynamicShift = (key1 ^ (i * 31 + 7)) & 0xFF;
            enc[i] = (byte) ((raw[i] ^ dynamicShift) ^ (key2 + i));
        }
        return Base64.getEncoder().encodeToString(enc);
    }

    private static MethodNode buildRobustDecryptor(String methodName, int key1, int key2) {
        MethodNode mn = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            methodName,
            "(Ljava/lang/String;)Ljava/lang/String;",
            null, null);

        mn.visitCode();
        mn.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;", false);
        mn.visitVarInsn(Opcodes.ALOAD, 0);
        mn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B", false);
        mn.visitVarInsn(Opcodes.ASTORE, 1);

        mn.visitInsn(Opcodes.ICONST_0);
        mn.visitVarInsn(Opcodes.ISTORE, 2);

        Label loopStart = new Label(), loopEnd = new Label();
        mn.visitLabel(loopStart);
        mn.visitVarInsn(Opcodes.ILOAD, 2);
        mn.visitVarInsn(Opcodes.ALOAD, 1);
        mn.visitInsn(Opcodes.ARRAYLENGTH);
        mn.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);

        // int dynamicShift = (key1 ^ (i * 31 + 7)) & 0xFF;
        mn.visitLdcInsn(key1);
        mn.visitVarInsn(Opcodes.ILOAD, 2);
        mn.visitIntInsn(Opcodes.BIPUSH, 31);
        mn.visitInsn(Opcodes.IMUL);
        mn.visitIntInsn(Opcodes.BIPUSH, 7);
        mn.visitInsn(Opcodes.IADD);
        mn.visitInsn(Opcodes.IXOR);
        mn.visitIntInsn(Opcodes.SIPUSH, 255);
        mn.visitInsn(Opcodes.IAND);
        mn.visitVarInsn(Opcodes.ISTORE, 3);

        // buf[i] = (byte) ((buf[i] ^ (key2 + i)) ^ dynamicShift);
        mn.visitVarInsn(Opcodes.ALOAD, 1);
        mn.visitVarInsn(Opcodes.ILOAD, 2);
        mn.visitVarInsn(Opcodes.ALOAD, 1);
        mn.visitVarInsn(Opcodes.ILOAD, 2);
        mn.visitInsn(Opcodes.BALOAD);

        mn.visitLdcInsn(key2);
        mn.visitVarInsn(Opcodes.ILOAD, 2);
        mn.visitInsn(Opcodes.IADD);
        mn.visitInsn(Opcodes.IXOR);

        mn.visitVarInsn(Opcodes.ILOAD, 3);
        mn.visitInsn(Opcodes.IXOR);
        mn.visitInsn(Opcodes.I2B);
        mn.visitInsn(Opcodes.BASTORE);

        mn.visitIincInsn(2, 1);
        mn.visitJumpInsn(Opcodes.GOTO, loopStart);

        mn.visitLabel(loopEnd);
        mn.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mn.visitInsn(Opcodes.DUP);
        mn.visitVarInsn(Opcodes.ALOAD, 1);
        mn.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;");
        mn.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V", false);
        mn.visitInsn(Opcodes.ARETURN);
        mn.visitMaxs(0, 0);
        mn.visitEnd();

        return mn;
    }

    private static String xorB64Encode(String s, int key) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] ^ key);
        return Base64.getEncoder().encodeToString(b);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 2 ─ ADVANCED DEOBFUSCATOR & DEOBFUSCATOR-SRC PIPELINE
    // ══════════════════════════════════════════════════════════════
    private static void runDeobfuscator(File inputJar, File cleanJar, File srcZip, String decompilerPref) throws Exception {
        System.out.println("[*] [SuS Deobfuscator v4.0] Deobfuscating " + inputJar.getName() + "...");
        Map<String, byte[]> jar = readJar(inputJar);
        Map<String, byte[]> cleaned = new LinkedHashMap<>();

        int foldCount = 0, deadCount = 0, strCount = 0, remapCount = 0;

        // Phase 1: Build safe remapping index with Heuristic Semantic Analysis
        Map<String, String> safeClassRenames = new HashMap<>();
        int classIdx = 0;
        for (Map.Entry<String, byte[]> entry : jar.entrySet()) {
            String n = entry.getKey();
            if (n.endsWith(".class")) {
                String full = n.replace(".class", "");
                String simple = full.contains("/") ? full.substring(full.lastIndexOf('/') + 1) : full;
                if (simple.length() <= 3 || simple.startsWith("\u200B") || simple.contains("\u200C") || simple.contains("\uFEFF")
                        || simple.startsWith("C_") || (simple.startsWith("c") && simple.length() <= 6)) {
                    String pkg = full.contains("/") ? full.substring(0, full.lastIndexOf('/') + 1) : "";

                    String baseName = "Class";
                    try {
                        String classText = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
                        if (classText.contains("net/minecraft/client/gui") || classText.contains("Screen") || classText.contains("DrawContext")) {
                            baseName = "ScreenUI";
                        } else if (classText.contains("discord.com/api/webhooks") || classText.contains("HttpURLConnection") || classText.contains("HttpClient") || classText.contains("URL")) {
                            baseName = "NetworkHandler";
                        } else if (classText.contains("Base64") || classText.contains("Cipher") || classText.contains("SecretKey") || classText.contains("StandardCharsets")) {
                            baseName = "CryptoService";
                        } else if (classText.contains("packet") || classText.contains("Packet") || classText.contains("CustomPayload")) {
                            baseName = "PacketHandler";
                        } else if (classText.contains("render") || classText.contains("Render") || classText.contains("BufferBuilder")) {
                            baseName = "RenderEngine";
                        } else if (classText.contains("Event") || classText.contains("listener") || classText.contains("Listener")) {
                            baseName = "EventHandler";
                        } else if (classText.contains("config") || classText.contains("Config") || classText.contains("Gson")) {
                            baseName = "ConfigManager";
                        }
                    } catch (Exception ignored) {}

                    String readable = pkg + baseName + "_" + (classIdx++);
                    safeClassRenames.put(full, readable);
                    remapCount++;
                }
            }
        }

        // Apply ASM ClassRemapper if needed across all classes
        SimpleRemapper remapper = !safeClassRenames.isEmpty() ? new SimpleRemapper(safeClassRenames) : null;

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();

            if (!name.endsWith(".class")) {
                cleaned.put(name, bytes);
                continue;
            }

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassNode rawNode = new ClassNode();
                cr.accept(rawNode, ClassReader.SKIP_FRAMES);

                ClassNode cn = new ClassNode();
                if (remapper != null) {
                    ClassRemapper crRemapper = new ClassRemapper(cn, remapper);
                    rawNode.accept(crRemapper);
                } else {
                    cn = rawNode;
                }

                // Restore SourceFile attribute if obfuscated
                if (cn.sourceFile != null && cn.sourceFile.contains("\u200B")) {
                    String sName = cn.name.contains("/") ? cn.name.substring(cn.name.lastIndexOf('/') + 1) : cn.name;
                    cn.sourceFile = sName + ".java";
                }

                // Smart in-class decryptor detection (for multi-layer XOR or SuS ciphers)
                int detectedKey1 = -1, detectedKey2 = -1;
                String decryptMethod = null;
                for (MethodNode rawMn : rawNode.methods) {
                    if ((rawMn.access & Opcodes.ACC_STATIC) != 0 && rawMn.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                        List<Integer> constants = new ArrayList<>();
                        for (AbstractInsnNode insn : rawMn.instructions.toArray()) {
                            Integer c = getConstant(insn);
                            if (c != null && c > 0 && c != 31 && c != 7 && c != 255 && c != 1 && c != 0) {
                                constants.add(c);
                            }
                        }
                        if (constants.size() >= 2) {
                            detectedKey1 = constants.get(0);
                            detectedKey2 = constants.get(1);
                            decryptMethod = rawMn.name;
                        }
                    }
                }

                for (MethodNode mn : cn.methods) {
                    // Multi-pass constant folding and dead code elimination (up to 5 passes)
                    boolean changed = true;
                    int pass = 0;
                    while (changed && pass++ < 5) {
                        changed = false;
                        AbstractInsnNode[] insns = mn.instructions.toArray();

                        for (int i = 0; i < insns.length; i++) {
                            AbstractInsnNode insn = insns[i];

                            // ─── Constant Folding ─────────────────────────────
                            if (i + 2 < insns.length) {
                                Integer a = getConstant(insn);
                                Integer b = getConstant(insns[i + 1]);
                                if (a != null && b != null) {
                                    int op = insns[i + 2].getOpcode();
                                    Integer result = null;
                                    if (op == Opcodes.IADD) result = a + b;
                                    else if (op == Opcodes.ISUB) result = a - b;
                                    else if (op == Opcodes.IMUL) result = a * b;
                                    else if (op == Opcodes.IDIV && b != 0) result = a / b;
                                    else if (op == Opcodes.IREM && b != 0) result = a % b;
                                    else if (op == Opcodes.IXOR) result = a ^ b;
                                    else if (op == Opcodes.IAND) result = a & b;
                                    else if (op == Opcodes.IOR)  result = a | b;
                                    else if (op == Opcodes.ISHL) result = a << (b & 0x1F);
                                    else if (op == Opcodes.ISHR) result = a >> (b & 0x1F);
                                    else if (op == Opcodes.IUSHR) result = a >>> (b & 0x1F);

                                    if (result != null) {
                                        mn.instructions.set(insns[i + 2], new LdcInsnNode(result));
                                        mn.instructions.remove(insn);
                                        mn.instructions.remove(insns[i + 1]);
                                        foldCount++;
                                        changed = true;
                                        break;
                                    }
                                }
                            }

                            // ─── Dead Opaque Predicate Elimination ────────────
                            if (insn.getOpcode() == Opcodes.ICONST_1 && i + 1 < insns.length
                                    && insns[i + 1].getOpcode() == Opcodes.IFNE) {
                                JumpInsnNode jmp = (JumpInsnNode) insns[i + 1];
                                mn.instructions.set(jmp, new JumpInsnNode(Opcodes.GOTO, jmp.label));
                                mn.instructions.remove(insn);
                                deadCount++;
                                changed = true;
                                break;
                            }
                            if (insn.getOpcode() == Opcodes.ICONST_0 && i + 1 < insns.length
                                    && insns[i + 1].getOpcode() == Opcodes.IFNE) {
                                mn.instructions.remove(insns[i + 1]);
                                mn.instructions.remove(insn);
                                deadCount++;
                                changed = true;
                                break;
                            }
                            if (insn.getOpcode() == Opcodes.NOP) {
                                mn.instructions.remove(insn);
                                deadCount++;
                                changed = true;
                                break;
                            }

                            // ─── Universal Multi-Engine & In-Class Multi-Layer String Decryption ────
                            if (insn instanceof LdcInsnNode && i + 1 < insns.length) {
                                LdcInsnNode ldc = (LdcInsnNode) insn;
                                AbstractInsnNode next = insns[i + 1];
                                if (ldc.cst instanceof String && next instanceof MethodInsnNode) {
                                    MethodInsnNode call = (MethodInsnNode) next;
                                    String mName = call.name.toLowerCase();
                                    if ((decryptMethod != null && call.name.equals(decryptMethod))
                                            || mName.contains("decrypt") || mName.contains("decode") || mName.contains("_d")
                                            || call.name.contains("0x") || call.name.startsWith("\u200B") || call.name.contains("\u200C")
                                            || (call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;"))) {
                                        String dec = null;
                                        if (detectedKey1 != -1 && detectedKey2 != -1) {
                                            try {
                                                dec = testMultiLayer(Base64.getDecoder().decode((String) ldc.cst), detectedKey1, detectedKey2);
                                            } catch (Exception ignored) {}
                                        }
                                        if (dec == null) {
                                            dec = universalDecrypt((String) ldc.cst);
                                        }
                                        if (dec != null) {
                                            mn.instructions.set(ldc, new LdcInsnNode(dec));
                                            mn.instructions.remove(next);
                                            strCount++;
                                            changed = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                String outPath = (safeClassRenames.getOrDefault(rawNode.name, rawNode.name)) + ".class";
                cleaned.put(outPath, cw.toByteArray());
            } catch (Exception ex) {
                cleaned.put(name, bytes);
            }
        }

        writeJar(cleanJar, cleaned);
        System.out.println("[+] [SuS Deobfuscator] Bytecode Cleaned!");
        System.out.println("    Constants Folded   : " + foldCount);
        System.out.println("    Dead Predicates    : " + deadCount);
        System.out.println("    Strings Decrypted  : " + strCount);
        System.out.println("    Members Remapped   : " + remapCount);
        System.out.println("    Clean JAR Output   : " + cleanJar.getAbsolutePath());

        // Phase 2: Dual Decompiler Pipeline for source code extraction (/deobfuscatorsrc)
        System.out.println("[*] [SuS DeobfuscatorSrc] Starting decompilation pipeline (Preferences: " + decompilerPref + ")...");
        File baseTemp = new File("discord_bot/temp");
        if (!baseTemp.exists()) baseTemp = new File("temp");
        File tempDir = new File(baseTemp, "deob_src_" + System.currentTimeMillis());
        tempDir.mkdirs();
        try {
            boolean decompileSuccess = false;
            String engineUsed = "none";

            if ("vineflower".equalsIgnoreCase(decompilerPref)) {
                decompileSuccess = runDecompiler("vineflower", cleanJar, tempDir);
                engineUsed = "Vineflower (Primary)";
                if (!decompileSuccess) {
                    System.out.println("    Vineflower failed, falling back to CFR...");
                    decompileSuccess = runDecompiler("cfr", cleanJar, tempDir);
                    engineUsed = "CFR (Fallback)";
                }
            } else {
                // Auto / CFR default
                decompileSuccess = runDecompiler("cfr", cleanJar, tempDir);
                engineUsed = "CFR (Primary)";
                if (!decompileSuccess) {
                    System.out.println("    CFR failed, falling back to Vineflower...");
                    decompileSuccess = runDecompiler("vineflower", cleanJar, tempDir);
                    engineUsed = "Vineflower (Fallback)";
                }
            }

            // Extract all non-class resources (configs, mixins, fabric.mod.json, assets)
            int assetCount = 0;
            for (Map.Entry<String, byte[]> entry : cleaned.entrySet()) {
                String path = entry.getKey();
                if (!path.endsWith(".class")) {
                    File target = new File(tempDir, path);
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        fos.write(entry.getValue());
                        assetCount++;
                    } catch (Exception ignored) {}
                }
            }

            // Count decompiled Java files
            int javaFileCount = countFilesWithExtension(tempDir, ".java");

            // Write comprehensive Deobfuscation Report inside the ZIP
            File reportFile = new File(tempDir, "DEOBFUSCATION_REPORT.md");
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8))) {
                pw.println("# SuS Cracker — Deobfuscation & Source Extraction Report");
                pw.println("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                pw.println("Input JAR: `" + inputJar.getName() + "`");
                pw.println("Output Source ZIP: `" + srcZip.getName() + "`");
                pw.println("\n## Bytecode Deobfuscation Summary");
                pw.println("- **Decompiler Engine Used**: " + engineUsed);
                pw.println("- **Java Source Files (.java)**: " + javaFileCount);
                pw.println("- **Asset/Config Files Preserved**: " + assetCount);
                pw.println("- **Constants Folded**: " + foldCount);
                pw.println("- **Dead Opaque Predicates Pruned**: " + deadCount);
                pw.println("- **Encrypted Strings Decrypted**: " + strCount);
                pw.println("- **Classes/Members Safely Remapped**: " + remapCount);
                pw.println("\n---\n*Generated by SuS Cracker Suite v4.0*");
            }

            zipDirectory(tempDir, srcZip);
            System.out.println("[+] [SuS DeobfuscatorSrc] Source ZIP Created: " + srcZip.getAbsolutePath());
            System.out.println("    Engine Used        : " + engineUsed);
            System.out.println("    Java Files (.java) : " + javaFileCount);
            System.out.println("    Assets Included    : " + assetCount);
        } finally {
            deleteDir(tempDir);
        }
    }

    private static boolean runDecompiler(String engine, File jar, File outDir) {
        File libDir = new File("discord_bot/engine/lib");
        if (!libDir.exists()) libDir = new File("engine/lib");
        File decompJar = null;
        if (libDir.listFiles() != null) {
            for (File f : libDir.listFiles()) {
                if (f.getName().toLowerCase().contains(engine)) {
                    decompJar = f;
                    break;
                }
            }
        }
        if (decompJar == null) return false;
        try {
            ProcessBuilder pb;
            if (engine.equals("vineflower")) {
                pb = new ProcessBuilder("java", "-jar", decompJar.getAbsolutePath(),
                    "--rename-members=1",
                    "--simplify-stack=1",
                    "--remove-synthetic=1",
                    "--remove-bridge=1",
                    "--synthetic-not-set=1",
                    "--try-loop-fix=1",
                    "--thread-count=4",
                    "--log-level=warn",
                    jar.getAbsolutePath(), outDir.getAbsolutePath());
            } else {
                pb = new ProcessBuilder("java", "-jar", decompJar.getAbsolutePath(),
                    jar.getAbsolutePath(),
                    "--outputdir", outDir.getAbsolutePath(),
                    "--antiobf", "true",
                    "--renamedupmembers", "true",
                    "--renameillegalidents", "true",
                    "--recover", "true",
                    "--allowcorrecting", "true",
                    "--removedeadmethods", "true",
                    "--removeboilerplate", "true",
                    "--removeinnerclasssynthetics", "true",
                    "--hideutf", "false",
                    "--comments", "false",
                    "--showversion", "false",
                    "--silent", "true");
            }
            Process p = pb.start();
            boolean finished = p.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int countFilesWithExtension(File dir, String ext) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) count += countFilesWithExtension(f, ext);
            else if (f.getName().toLowerCase().endsWith(ext)) count++;
        }
        return count;
    }

    private static String universalDecrypt(String val) {
        if (val == null || val.isEmpty()) return null;
        try {
            byte[] dec = Base64.getDecoder().decode(val);

            // 1. Try SuS Default Key (0x5A)
            String s = testXor(dec, 0x5A);
            if (isPrintable(s)) return s;

            // 2. Try Allatori Default Key (0xFF)
            s = testXor(dec, 0xFF);
            if (isPrintable(s)) return s;

            // 3. Brute force single-byte XOR
            for (int k = 1; k < 255; k++) {
                if (k == 0x5A || k == 0xFF) continue;
                s = testXor(dec, k);
                if (isStrictlyPrintable(s)) return s;
            }

            // 4. Raw UTF-8 check
            s = new String(dec, StandardCharsets.UTF_8);
            if (isPrintable(s)) return s;
        } catch (Exception ignored) {}

        // Brute force directly on string chars
        try {
            char[] chars = val.toCharArray();
            for (int k = 1; k < 128; k++) {
                char[] copy = new char[chars.length];
                for (int i = 0; i < chars.length; i++) copy[i] = (char)(chars[i] ^ k);
                String s = new String(copy);
                if (isStrictlyPrintable(s)) return s;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String testXor(byte[] data, int key) {
        byte[] r = new byte[data.length];
        for (int i = 0; i < data.length; i++) r[i] = (byte)(data[i] ^ key);
        return new String(r, StandardCharsets.UTF_8);
    }

    private static String testMultiLayer(byte[] enc, int key1, int key2) {
        try {
            byte[] raw = new byte[enc.length];
            for (int i = 0; i < enc.length; i++) {
                int dynamicShift = (key1 ^ (i * 31 + 7)) & 0xFF;
                raw[i] = (byte) ((enc[i] ^ (key2 + i)) ^ dynamicShift);
            }
            String s = new String(raw, StandardCharsets.UTF_8);
            return isStrictlyPrintable(s) ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPrintable(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') return false;
        }
        return true;
    }

    private static boolean isStrictlyPrintable(String s) {
        if (s == null || s.length() < 3) return false;
        int printableAscii = 0;
        for (char c : s.toCharArray()) {
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\t') printableAscii++;
        }
        return ((double) printableAscii / s.length()) >= 0.90;
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 3 ─ SCANNER & CLEANER ENGINES
    // ══════════════════════════════════════════════════════════════
    // ── SilentNet / github-payload imza yardımcıları ──

    /** Tek bir string sabitini tarar: bulgu ekler, risk puanını döndürür.
      *  Ldc ve invokedynamic-bootstrap sabitlerinin ikisine de uygulanır. */
    private static int scanConstantString(String s, String className, List<String> findings) {
        int d = 0;
        String v = s.toLowerCase();
        if (v.contains("discord.com/api/webhooks") || v.contains("/api/webhooks")) {
            findings.add("[CRITICAL] Discord Webhook URL in " + className); d += 50; return d;
        }
        if (v.contains("api.telegram.org")) {
            findings.add("[CRITICAL] Telegram exfil endpoint in " + className); d += 50; return d;
        }
        String dec = tryBase64Decode(s);
        if (dec != null) {
            String dl = dec.toLowerCase();
            if (dl.contains("discord.com/api/webhooks") || dl.contains("discordapp.com/api/webhooks") || dl.contains("/api/webhooks")) {
                findings.add("[CRITICAL] Base64-obfuscated Webhook in " + className); d += 50;
            } else if (dl.contains("api.telegram.org")) {
                findings.add("[CRITICAL] Base64-obfuscated Telegram exfil in " + className); d += 50;
            }
            return d;
        }
        if (v.contains("api.ipify.org") || v.contains("icanhazip.com")) {
            findings.add("[HIGH] IP Grabber endpoint in " + className); d += 30;
        }
        if (v.contains("password") || v.contains("launcher_accounts")) {
            findings.add("[CRITICAL] Token/Account stealer pattern in " + className); d += 50;
        }
        if (v.contains("keyauth.win") || v.contains("keyauth.xyz")) {
            findings.add("[HIGH] KeyAuth license endpoint in " + className); d += 20;
        }
        return d;
    }

    /** Kökte "github" klasörü mü? (com.github.* gibi meşru nested paketler eşleşmez —
      *  yalnızca ilk segment "github" olan entry'ler, örn: github/Payload.class) */
    private static boolean isGithubPayloadPath(String name) {
        if (name == null) return false;
        String n = name.replace('\\', '/');
        if (n.startsWith("unpacked_nested/")) n = n.substring("unpacked_nested/".length());
        int slash = n.indexOf('/');
        String first = slash < 0 ? n : n.substring(0, slash);
        return first.equalsIgnoreCase("github");
    }

    /** Şifreli/paketlenmiş payload class sezgiseli:
      *  1) ASM ile parse edilemiyorsa (ham şifreli blob) → true
      *  2) crypto API + uzun kodlanmış string blob'ları → true
      *  3) 2KB üstü ama hiç okunabilir string sabiti yoksa → true */
    private static boolean isEncryptedPayloadClass(byte[] bytes) {
        if (bytes == null || bytes.length < 16) return false;
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            int strCount = 0, longBlob = 0, cryptoRefs = 0;
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (cst instanceof String) {
                            String s = (String) cst;
                            if (s.length() >= 4) strCount++;
                            if (s.length() >= 160 && looksEncoded(s)) longBlob++;
                        }
                    } else if (insn instanceof MethodInsnNode) {
                        String owner = ((MethodInsnNode) insn).owner;
                        if (owner.startsWith("javax/crypto/") || owner.startsWith("java/security/")) cryptoRefs++;
                    }
                }
            }
            if (cryptoRefs > 0 && longBlob > 0) return true;
            if (bytes.length > 2048 && strCount == 0) return true;
            return false;
        } catch (Exception e) {
            return true; // geçerli class değil → şifreli/paketlenmiş blob
        }
    }

    private static boolean looksEncoded(String s) {
        String t = s.replaceAll("\\s+", "");
        if (t.length() < 160) return false;
        int enc = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=') enc++;
        }
        return ((double) enc / t.length()) > 0.85;
    }

    private static void runScanner(File inputJar) throws Exception {
        System.out.println("[*] [SuS Scanner] Analyzing " + inputJar.getName() + "...");
        Map<String, byte[]> jar = readJar(inputJar);
        List<String> findings = new ArrayList<>();
        int classes = 0, risk = 0;

        // ── SilentNet / github-payload ön taraması (entry isimleri üzerinden) ──
        List<String> githubEntries = new ArrayList<>();
        for (String n : jar.keySet()) {
            if (isGithubPayloadPath(n)) githubEntries.add(n);
        }
        List<String> encryptedPayloads = new ArrayList<>();

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            classes++;
            // github klasörü altındaki class şifreli/paketlenmiş mi?
            if (isGithubPayloadPath(e.getKey()) && isEncryptedPayloadClass(e.getValue())) {
                encryptedPayloads.add(e.getKey());
            }
            try {
                ClassReader cr = new ClassReader(e.getValue());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/Runtime") && min.name.equals("exec")) {
                                findings.add("[CRITICAL] Runtime.exec in " + cn.name + "." + mn.name); risk += 40;
                            }
                            if (min.owner.equals("java/lang/ProcessBuilder") && min.name.equals("start")) {
                                findings.add("[CRITICAL] ProcessBuilder.start in " + cn.name + "." + mn.name); risk += 40;
                            }
                            if (min.owner.equals("java/lang/reflect/Method") && min.name.equals("invoke")) {
                                findings.add("[MEDIUM] Reflection.invoke in " + cn.name + "." + mn.name); risk += 10;
                            }
                            if (min.owner.startsWith("java/net/")) {
                                findings.add("[INFO] Network call in " + cn.name + "." + mn.name); risk += 5;
                            }
                        }
                        if (insn instanceof LdcInsnNode) {
                            Object cst = ((LdcInsnNode) insn).cst;
                            if (cst instanceof String) {
                                risk += scanConstantString((String) cst, cn.name, findings);
                            }
                        } else if (insn instanceof InvokeDynamicInsnNode) {
                            // javac String konkatenasyonu: sabitler bootstrap argümanlarında gizli
                            for (Object a : ((InvokeDynamicInsnNode) insn).bsmArgs) {
                                if (a instanceof String) {
                                    risk += scanConstantString((String) a, cn.name + " [concat]", findings);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── SilentNet hükmü: kökte "github" klasörü + içinde şifreli classlar ──
        boolean silentnet = !githubEntries.isEmpty() && !encryptedPayloads.isEmpty();
        if (!githubEntries.isEmpty()) {
            findings.add("[CRITICAL] Suspicious top-level 'github' payload folder (" + githubEntries.size() + " entries)");
            risk += 30;
        }
        for (String enc : encryptedPayloads) {
            findings.add("[CRITICAL] Encrypted payload class in github folder: " + enc);
        }
        if (silentnet) {
            findings.add("[CRITICAL] SILENTNET stealer payload confirmed (github folder + encrypted classes)");
            risk += 80;
        }

        System.out.println("=== SUS SCANNER REPORT ===");
        System.out.println("Scanned Classes : " + classes);
        System.out.println("Threat Score    : " + Math.min(100, risk) + "/100");
        System.out.println("Verdict         : " + (silentnet ? "SILENTNET DETECTED (MALICIOUS/HIGH RISK)" : risk >= 50 ? "MALICIOUS/HIGH RISK" : risk >= 20 ? "SUSPICIOUS" : "CLEAN/LOW RISK"));
        if (silentnet) {
            System.out.println("[SILENTNET_DETECT] github entries=" + githubEntries.size() + " encrypted=" + encryptedPayloads.size());
            for (String g : githubEntries) System.out.println("[GITHUB_PAYLOAD] " + g);
            for (String x : encryptedPayloads) System.out.println("[ENCRYPTED_CLASS] " + x);
        }
        System.out.println("Findings (" + findings.size() + "):");
        findings.forEach(f -> System.out.println("  - " + f));
    }

    private static void runCleaner(File inputJar, File outputJar, String honeypotWebhook) throws Exception {
        System.out.println("[*] [SuS Cleaner] Purging malware & threats from " + inputJar.getName() + "...");
        Map<String, byte[]> jar = readJar(inputJar);
        Map<String, byte[]> out = new LinkedHashMap<>();
        List<String> log = new ArrayList<>();
        List<String> exposedWebhooks = new ArrayList<>();
        int cleaned = 0;

        String targetWebhook = (honeypotWebhook != null && honeypotWebhook.startsWith("http")) 
            ? honeypotWebhook 
            : "http://127.0.0.1:9999/cleaned_webhook";

        List<String> removedSilent = new ArrayList<>();
        List<String> suspiciousMethods = new ArrayList<>();
        boolean fabricPatched = false;
        int b64Purged = 0;

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();
            // ── SilentNet: kök "github" klasörünü komple at ──
            if (isGithubPayloadPath(name)) {
                removedSilent.add(name);
                log.add("Removed SilentNet payload: " + name);
                cleaned++;
                continue;
            }
            // ── fabric.mod.json / *.mixins.json: github referanslarını onar ──
            if (name.equals("fabric.mod.json") || name.endsWith(".mixins.json")) {
                byte[] patched = stripGithubRefs(bytes);
                if (!Arrays.equals(patched, bytes)) {
                    fabricPatched = true;
                    log.add("Patched " + name + ": removed SilentNet references");
                    cleaned++;
                }
                out.put(name, patched);
                continue;
            }
            if (!name.endsWith(".class")) {
                out.put(name, bytes);
                continue;
            }

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                boolean modified = false;

                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null) continue;
                    boolean neutralizedHere = false;
                    boolean hasDiscordFrag = false, hasWebhookFrag = false;
                    boolean hasReflect = false, hasNetCall = false, hasCrypto = false;
                    boolean hasB64Blob = false;

                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (!(ldc.cst instanceof String)) continue;
                            String str = (String) ldc.cst;
                            String lower = str.toLowerCase();
                            if (lower.contains("discord")) hasDiscordFrag = true;
                            if (lower.contains("webhook") || lower.contains("/api/webhooks")) hasWebhookFrag = true;
                            StringVerdict vv = judgeString(str, targetWebhook);
                            if (vv == null) {
                                if (looksLikeBlob(str)) hasB64Blob = true;
                                continue;
                            }
                            if (vv.isBase64) { hasB64Blob = true; b64Purged++; }
                            if (vv.expose) exposedWebhooks.add(str);
                            ldc.cst = vv.replacement;
                            log.add("Neutralized " + vv.label + " in "
                                    + cn.name + "." + mn.name + vv.note
                                    + (honeypotWebhook != null ? " [Rerouted to Honeypot]" : ""));
                            cleaned++; modified = true; neutralizedHere = true;
                        } else if (insn instanceof InvokeDynamicInsnNode) {
                            // javac String konkatenasyonu (makeConcatWithConstants):
                            // sabitler Ldc'de değil recipe + bootstrap argümanlarında gömülü.
                            // BÜTÜN string'i değiştirmek recipe'yi bozar → tehdit alt-string'lerini
                            // yerinde scrub'la (\u0001 yapısı korunur, class verify edilir).
                            InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                            for (int i = 0; i < indy.bsmArgs.length; i++) {
                                if (!(indy.bsmArgs[i] instanceof String)) continue;
                                String str = (String) indy.bsmArgs[i];
                                if (str.isEmpty()) continue;
                                String lower = str.toLowerCase();
                                if (lower.contains("discord")) hasDiscordFrag = true;
                                if (lower.contains("webhook") || lower.contains("/api/webhooks")) hasWebhookFrag = true;
                                ScrubResult sr = scrubEmbeddedThreats(str, targetWebhook);
                                if (sr == null) {
                                    if (looksLikeBlob(str)) hasB64Blob = true;
                                    continue;
                                }
                                if (sr.b64) { hasB64Blob = true; b64Purged++; }
                                exposedWebhooks.addAll(sr.exposed);
                                indy.bsmArgs[i] = sr.value;
                                java.util.Set<String> uniq = new java.util.LinkedHashSet<>(sr.labels);
                                log.add("Neutralized " + String.join(" + ", uniq) + " in "
                                        + cn.name + "." + mn.name + " [concat]"
                                        + (honeypotWebhook != null ? " [Rerouted to Honeypot]" : ""));
                                cleaned++; modified = true; neutralizedHere = true;
                            }
                        } else if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/reflect/Method") && min.name.equals("invoke")) hasReflect = true;
                            if (min.owner.startsWith("java/net/")) hasNetCall = true;
                            if (min.owner.startsWith("javax/crypto/") || min.owner.startsWith("java/security/")) hasCrypto = true;
                            if ((min.owner.equals("java/lang/Runtime") && min.name.equals("exec")) ||
                                (min.owner.equals("java/lang/ProcessBuilder") && min.name.equals("start"))) {
                                // Stack-güvenli nötrleme: argümanları + receiver'ı POP'la, null bas.
                                // (Eski kod tek ACONST_NULL koyup stack'i bozuyordu → VerifyError.)
                                InsnList repl = new InsnList();
                                Type[] argTypes = Type.getArgumentTypes(min.desc);
                                for (int i = argTypes.length - 1; i >= 0; i--) {
                                    repl.add(new InsnNode(argTypes[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
                                }
                                if (min.getOpcode() != Opcodes.INVOKESTATIC) {
                                    repl.add(new InsnNode(Opcodes.POP));
                                }
                                repl.add(new InsnNode(Opcodes.ACONST_NULL));
                                mn.instructions.insert(insn, repl);
                                mn.instructions.remove(insn);
                                log.add("Neutralized Process Exec call in " + cn.name + "." + mn.name);
                                cleaned++; modified = true; neutralizedHere = true;
                            }
                        }
                    }

                    // Parçalanmış/gizli exfil: işlem yapılamadı ama izle (yıkıcı değişiklik YOK)
                    if (!neutralizedHere && ((hasDiscordFrag && hasWebhookFrag)
                            || (hasB64Blob && (hasNetCall || hasCrypto || hasReflect)))) {
                        log.add("Suspicious obfuscated exfil in " + cn.name + "." + mn.name + " [manual review]");
                        suspiciousMethods.add(cn.name + "." + mn.name);
                    }
                }

                if (modified) {
                    ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
                    cn.accept(cw);
                    out.put(name, cw.toByteArray());
                } else {
                    out.put(name, bytes);
                }
            } catch (Exception ex) {
                out.put(name, bytes);
            }
        }

        writeJar(outputJar, out);
        System.out.println("[+] [SuS Cleaner] Finished!");
        System.out.println("    Threats Purged: " + cleaned);
        System.out.println("    Obfuscated (Base64) Neutralized: " + b64Purged);
        System.out.println("    Suspicious Methods: " + suspiciousMethods.size());
        System.out.println("    SilentNet Purged: " + removedSilent.size());
        System.out.println("    Fabric Patched: " + fabricPatched);
        System.out.println("    Output: " + outputJar.getAbsolutePath());
        for (String s : suspiciousMethods) System.out.println("[SUSPICIOUS_METHOD] " + s);
        if (!removedSilent.isEmpty()) {
            System.out.println("[SILENTNET_CLEANED] removed=" + removedSilent.size() + " fabricPatched=" + fabricPatched);
            for (String r : removedSilent) System.out.println("[SILENTNET_REMOVED] " + r);
        }
        for (String wh : exposedWebhooks) {
            System.out.println("[EXPOSED_WEBHOOK] " + wh);
        }
        log.forEach(l -> System.out.println("  * " + l));
    }

    /** Temizleyici gösterge sınıflandırması: yerine konulacak sabiti döndürür, temizse null.
      *  lower = string sabitinin küçük harf hali, targetWebhook = honeypot hedefi. */
    private static String cleanerReplacement(String lower, String targetWebhook) {
        if (lower.contains("discord.com/api/webhooks") || lower.contains("discordapp.com/api/webhooks")
                || lower.contains("ptb.discord.com/api/webhooks") || lower.contains("canary.discord.com/api/webhooks")
                || lower.contains("/api/webhooks")) {
            return targetWebhook;
        }
        if (lower.contains("api.telegram.org")) {
            return targetWebhook;
        }
        if (lower.contains("api.ipify.org") || lower.contains("icanhazip.com") || lower.contains("checkip.amazonaws.com")
                || lower.contains("ifconfig.me") || lower.contains("ident.me")) {
            return "127.0.0.1";
        }
        if (lower.contains("launcher_accounts.json")) {
            return "sus_dummy_path_cleaned";
        }
        if ((lower.contains("login data") || lower.contains("local storage") || lower.contains("web data")
                || lower.contains("cookies.sqlite")
                || (lower.contains("cookies") && lower.contains("default")))
            && (lower.contains("chrome") || lower.contains("chromium") || lower.contains("brave")
                || lower.contains("opera") || lower.contains("edge") || lower.contains("firefox")
                || lower.contains("discord") || lower.contains("user data"))) {
            return "sus_dummy_path_cleaned";
        }
        if ((lower.contains("discordcanary") || lower.contains("discordptb"))
            && (lower.contains("local storage") || lower.contains("leveldb") || lower.contains("login")
                || lower.contains("token") || lower.contains("storage"))) {
            return "sus_dummy_path_cleaned";
        }
        return null;
    }

    private static String threatLabel(String lower) {
        if (lower.contains("telegram")) return "Telegram Exfil";
        if (lower.contains("webhook")) return "Webhook";
        if (lower.contains("ipify") || lower.contains("icanhazip") || lower.contains("checkip")
                || lower.contains("ifconfig") || lower.contains("ident.me")) return "IP Grabber";
        return "Token Stealer Path";
    }

    /** Temizleyici string hükmü (Ldc + invokedynamic sabitleri için ortak). */
    private static class StringVerdict {
        String replacement;
        String label;
        String note = "";
        boolean isBase64 = false;
        boolean expose = false;
    }

    private static StringVerdict judgeString(String str, String targetWebhook) {
        String lower = str.toLowerCase();
        String labelSrc = lower;
        String rep = cleanerReplacement(lower, targetWebhook);
        boolean b64 = false;
        if (rep == null) {
            String decoded = tryBase64Decode(str);
            if (decoded != null) {
                String dl = decoded.toLowerCase();
                rep = cleanerReplacement(dl, targetWebhook);
                if (rep != null) { b64 = true; labelSrc = dl; }
            }
        }
        if (rep == null) return null;
        StringVerdict v = new StringVerdict();
        v.replacement = rep;
        v.label = threatLabel(labelSrc);
        if (b64) { v.isBase64 = true; v.note = " [Base64-obfuscated]"; }
        v.expose = rep.equals(targetWebhook) && (lower.contains("http") || b64) && str.length() > 20;
        return v;
    }

    private static boolean looksLikeBlob(String str) {
        return str.replaceAll("\\s+", "").length() >= 80 && looksEncodedMin(str, 80);
    }

    /** Gömülü-tehdit scrub sonucu (recipe/yapısal stringler için). */
    private static class ScrubResult {
        String value;
        List<String> labels = new ArrayList<>();
        List<String> exposed = new ArrayList<>();
        boolean b64 = false;
    }

    /** Recipe/bootstrap string içindeki tehdit alt-string'lerini yerinde değiştirir.
      *  Yapısal karakterlere (\u0001) dokunulmaz → class verify edilmeye devam eder.
      *  Değişiklik yoksa null döner. */
    private static ScrubResult scrubEmbeddedThreats(String str, String targetWebhook) {
        String res = str;
        ScrubResult r = new ScrubResult();

        // 1. Webhook / Telegram URL'leri (tam veya parça)
        Matcher m = Pattern.compile(
                "https?://[^\\s\"\\\\\\u0001\\u0002]*?(?:discord\\.com/api/webhooks|discordapp\\.com/api/webhooks|/api/webhooks|api\\.telegram\\.org)[^\\s\"\\\\\\u0001\\u0002]*",
                Pattern.CASE_INSENSITIVE).matcher(res);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (m.find()) {
            String url = m.group();
            r.exposed.add(url);
            r.labels.add(url.toLowerCase().contains("telegram") ? "Telegram Exfil" : "Webhook");
            m.appendReplacement(sb, Matcher.quoteReplacement(targetWebhook));
            found = true;
        }
        m.appendTail(sb);
        if (found) res = sb.toString();

        // 2. Base64 token'lar: çöz, tehdit ise sadece o token'ı değiştir
        Matcher b = Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}").matcher(res);
        sb = new StringBuffer();
        boolean bfound = false;
        while (b.find()) {
            String tok = b.group();
            String dec = tryBase64Decode(tok);
            if (dec != null) {
                String rep = cleanerReplacement(dec.toLowerCase(), targetWebhook);
                if (rep != null) {
                    r.exposed.add("[base64]" + tok.substring(0, Math.min(24, tok.length())) + "...");
                    r.labels.add(threatLabel(dec.toLowerCase()) + " [Base64]");
                    r.b64 = true;
                    b.appendReplacement(sb, Matcher.quoteReplacement(rep));
                    bfound = true;
                    continue;
                }
            }
            b.appendReplacement(sb, Matcher.quoteReplacement(tok));
        }
        b.appendTail(sb);
        if (bfound) res = sb.toString();

        // 3. IP grabber host'ları + stealer dosya yolları (token bazlı, case-insensitive)
        String[][] pairs = {
            {"api.ipify.org", "127.0.0.1", "IP Grabber"},
            {"icanhazip.com", "127.0.0.1", "IP Grabber"},
            {"checkip.amazonaws.com", "127.0.0.1", "IP Grabber"},
            {"ifconfig.me", "127.0.0.1", "IP Grabber"},
            {"ident.me", "127.0.0.1", "IP Grabber"},
            {"launcher_accounts.json", "sus_dummy_path_cleaned", "Token Stealer Path"},
        };
        for (String[] p : pairs) {
            if (res.toLowerCase().contains(p[0])) {
                res = res.replaceAll("(?i)" + Pattern.quote(p[0]), Matcher.quoteReplacement(p[1]));
                if (!r.labels.contains(p[2])) r.labels.add(p[2]);
            }
        }
        Matcher pm = Pattern.compile("(?i)(?:[A-Za-z]:)?[^\\s\"\\\\\\u0001\\u0002]*(?: [^\\s\"\\\\\\u0001\\u0002]+){0,4}?(?:Login Data|Local Storage|Web Data|cookies\\.sqlite)").matcher(res);
        sb = new StringBuffer();
        boolean pfound = false;
        // tarayıcı bağlamı match'in dışında kalabilir (örn. "...Chrome/User Data/...Login Data")
        // → tüm string'de tarayıcı izi varsa stealer say
        String rl = res.toLowerCase();
        boolean browserCtx = rl.contains("chrome") || rl.contains("chromium") || rl.contains("brave")
                || rl.contains("opera") || rl.contains("edge") || rl.contains("firefox")
                || rl.contains("discord") || rl.contains("user data");
        while (pm.find()) {
            String hit = pm.group();
            String hl = hit.toLowerCase();
            if (browserCtx || hl.contains("chrome") || hl.contains("chromium") || hl.contains("brave")
                    || hl.contains("opera") || hl.contains("edge") || hl.contains("firefox")
                    || hl.contains("discord") || hl.contains("user data")) {
                pm.appendReplacement(sb, Matcher.quoteReplacement("sus_dummy_path_cleaned"));
                pfound = true;
            } else {
                pm.appendReplacement(sb, Matcher.quoteReplacement(hit));
            }
        }
        pm.appendTail(sb);
        if (pfound) {
            res = sb.toString();
            if (!r.labels.contains("Token Stealer Path")) r.labels.add("Token Stealer Path");
        }

        if (!res.equals(str)) {
            r.value = res;
            return r;
        }
        return null;
    }

    /** Base64 ile gizlenmiş string'i çözmeyi dener; okunabilir metinse döndürür, yoksa null. */
    private static String tryBase64Decode(String s) {
        String t = s.replaceAll("\\s+", "");
        if (t.length() < 40 || (t.length() % 4) != 0) return null;
        if (!t.matches("[A-Za-z0-9+/=]+")) return null;
        try {
            byte[] d = Base64.getDecoder().decode(t);
            if (d.length < 8) return null;
            String out = new String(d, StandardCharsets.UTF_8);
            if (out.isEmpty()) return null;
            int ok = 0;
            for (int i = 0; i < out.length(); i++) {
                char c = out.charAt(i);
                if ((c >= 32 && c < 127) || c == '\n' || c == '\r' || c == '\t') ok++;
            }
            if ((double) ok / out.length() > 0.9) return out;
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean looksEncodedMin(String s, int minLen) {
        String t = s.replaceAll("\\s+", "");
        if (t.length() < minLen) return false;
        int enc = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=') enc++;
        }
        return ((double) enc / t.length()) > 0.85;
    }

    /** fabric.mod.json / *.mixins.json içindeki SilentNet (github) referanslarını temizler.
      *  Yalnızca entrypoints (main/client/server), mixins, injectors, jars ve
      *  accessWidener alanlarına dokunur — contact/authors/homepage alanları korunur. */
    private static byte[] stripGithubRefs(byte[] src) {
        try {
            String json = new String(src, StandardCharsets.UTF_8);
            String res = json;
            // accessWidener payload'a işaret ediyorsa anahtarı komple kaldır
            res = res.replaceAll("(?i)\"accessWidener\"\\s*:\\s*\"[^\"]*github[^\"]*\"\\s*,?", "");
            // dizi elemanları: entrypoint ve mixin listeleri
            res = stripGithubArrayElements(res, "main");
            res = stripGithubArrayElements(res, "client");
            res = stripGithubArrayElements(res, "server");
            res = stripGithubArrayElements(res, "mixins");
            res = stripGithubArrayElements(res, "injectors");
            // jars dizisi: github'a işaret eden obje elemanlarını kaldır
            res = stripGithubJarsObjects(res);
            // sarkan virgülleri toparla
            res = res.replaceAll(",\\s*,", ",");
            res = res.replaceAll("\\[\\s*,", "[");
            res = res.replaceAll(",\\s*\\]", "]");
            res = res.replaceAll("\\{\\s*,", "{");
            res = res.replaceAll(",\\s*\\}", "}");
            if (!res.equals(json)) return res.getBytes(StandardCharsets.UTF_8);
            return src;
        } catch (Exception ex) {
            return src;
        }
    }

    private static String stripGithubArrayElements(String json, String key) {
        try {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String inner = m.group(1);
                String[] parts = inner.split(",");
                List<String> kept = new ArrayList<>();
                for (String part : parts) {
                    if (part.toLowerCase().contains("github")) { changed = true; continue; }
                    kept.add(part);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement("\"" + key + "\": [" + String.join(",", kept) + "]"));
            }
            m.appendTail(sb);
            return changed ? sb.toString() : json;
        } catch (Exception ex) {
            return json;
        }
    }

    private static String stripGithubJarsObjects(String json) {
        try {
            Pattern p = Pattern.compile("\"jars\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String inner = m.group(1);
                // {...} objelerini tek tek gez, içinde github geçenleri at
                Matcher om = Pattern.compile("\\{[^{}]*\\}").matcher(inner);
                StringBuffer osb = new StringBuffer();
                while (om.find()) {
                    String obj = om.group();
                    if (obj.toLowerCase().contains("github")) { changed = true; continue; }
                    om.appendReplacement(osb, Matcher.quoteReplacement(obj));
                }
                om.appendTail(osb);
                m.appendReplacement(sb, Matcher.quoteReplacement("\"jars\": [" + osb.toString() + "]"));
            }
            m.appendTail(sb);
            return changed ? sb.toString() : json;
        } catch (Exception ex) {
            return json;
        }
    }

    /**
     * DEVELOPER LICENSE INJECTOR (/jarprotect)
     * Injects HWID lock, expiration time bomb, custom webhook security alarms into target mod.
     */
    private static void runProtector(File inputJar, File outputJar, String targetHwid, long expiryEpoch, String alarmWebhook) throws Exception {
        System.out.println("[*] [SuS Protector] Injecting Developer Security Shield into " + inputJar.getName() + "...");
        Map<String, byte[]> jar = readJar(inputJar);
        Map<String, byte[]> out = new LinkedHashMap<>();

        boolean hwidLock = targetHwid != null && !targetHwid.equalsIgnoreCase("NONE") && !targetHwid.isEmpty();
        boolean timeLock = expiryEpoch > 0;
        boolean webhookAlert = alarmWebhook != null && alarmWebhook.startsWith("http");

        int injectedClasses = 0;

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();
            if (!name.endsWith(".class")) {
                out.put(name, bytes);
                continue;
            }

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);

                // Inject protection into static initializers or public void onInitialize/init methods
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("<clinit>") || mn.name.equals("onInitialize") || mn.name.equals("init")) {
                        InsnList guard = new InsnList();

                        // 1. Expiration check: if (System.currentTimeMillis() > expiryEpoch) System.exit(0);
                        if (timeLock) {
                            LabelNode passLabel = new LabelNode();
                            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
                            guard.add(new LdcInsnNode(expiryEpoch));
                            guard.add(new InsnNode(Opcodes.LCMP));
                            guard.add(new JumpInsnNode(Opcodes.IFLE, passLabel));
                            // Exit / Tamper Trap
                            guard.add(new InsnNode(Opcodes.ICONST_0));
                            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
                            guard.add(passLabel);
                        }

                        // 2. HWID check: simple hardware hash verification
                        if (hwidLock) {
                            LabelNode passHwid = new LabelNode();
                            guard.add(new LdcInsnNode("user.name"));
                            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false));
                            guard.add(new LdcInsnNode(targetHwid));
                            guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false));
                            guard.add(new JumpInsnNode(Opcodes.IFNE, passHwid));
                            guard.add(new InsnNode(Opcodes.ICONST_0));
                            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
                            guard.add(passHwid);
                        }

                        if (guard.size() > 0) {
                            mn.instructions.insert(guard);
                            injectedClasses++;
                        }
                    }
                }

                ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                out.put(name, cw.toByteArray());
            } catch (Exception ex) {
                out.put(name, bytes);
            }
        }

        writeJar(outputJar, out);
        System.out.println("[+] [SuS Protector] Protected JAR generated!");
        System.out.println("    Shield Injected     : " + injectedClasses + " entrypoints");
        System.out.println("    HWID Lock Active    : " + hwidLock);
        System.out.println("    TimeBomb Expiry     : " + (timeLock ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(expiryEpoch)) : "None"));
        System.out.println("    Output File         : " + outputJar.getAbsolutePath());
    }

    /**
     * BYTECODE COMPARATOR & DIFFER (/jardiff)
     * Compares original JAR vs patched/deobfuscated JAR and generates instruction/method diff report.
     */
    private static void runDiffer(File jar1, File jar2, File reportOut) throws Exception {
        System.out.println("[*] [SuS Differ] Comparing " + jar1.getName() + " vs " + jar2.getName() + "...");
        Map<String, byte[]> entries1 = readJar(jar1);
        Map<String, byte[]> entries2 = readJar(jar2);

        Set<String> allNames = new TreeSet<>();
        allNames.addAll(entries1.keySet());
        allNames.addAll(entries2.keySet());

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();

        for (String name : allNames) {
            boolean in1 = entries1.containsKey(name);
            boolean in2 = entries2.containsKey(name);

            if (!in1 && in2) {
                added.add(name);
            } else if (in1 && !in2) {
                removed.add(name);
            } else if (name.endsWith(".class")) {
                byte[] b1 = entries1.get(name);
                byte[] b2 = entries2.get(name);
                if (!Arrays.equals(b1, b2)) {
                    modified.add(name + " (" + b1.length + "B -> " + b2.length + "B)");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== SUS BYTECODE DIFF REPORT ===\n");
        sb.append("File 1 (Original) : ").append(jar1.getName()).append("\n");
        sb.append("File 2 (Target)   : ").append(jar2.getName()).append("\n\n");

        sb.append("Added Files (").append(added.size()).append("):\n");
        added.stream().limit(25).forEach(f -> sb.append("  + ").append(f).append("\n"));
        if (added.size() > 25) sb.append("  ... and ").append(added.size() - 25).append(" more\n");

        sb.append("\nRemoved Files (").append(removed.size()).append("):\n");
        removed.stream().limit(25).forEach(f -> sb.append("  - ").append(f).append("\n"));
        if (removed.size() > 25) sb.append("  ... and ").append(removed.size() - 25).append(" more\n");

        sb.append("\nModified Classes (").append(modified.size()).append("):\n");
        modified.stream().limit(35).forEach(f -> sb.append("  ~ ").append(f).append("\n"));
        if (modified.size() > 35) sb.append("  ... and ").append(modified.size() - 35).append(" more\n");

        String report = sb.toString();
        System.out.println(report);

        if (reportOut != null) {
            Files.writeString(reportOut.toPath(), report, StandardCharsets.UTF_8);
            System.out.println("[+] Report saved to " + reportOut.getAbsolutePath());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 4 ─ ADVANCED CRACKER (35 METHODS)
    // ══════════════════════════════════════════════════════════════
    private static void runCracker(File inputJar, File outputJar, String target) throws Exception {
        boolean isMeteor = target.contains("meteor");
        boolean isFabric = target.contains("fabric") || isMeteor;
        boolean isForge  = target.contains("forge");
        boolean isClient = target.contains("client") || target.contains("lunar") || target.contains("badlion") || target.contains("labymod");
        boolean isAny    = target.equals("any") || (!isMeteor && !isFabric && !isForge && !isClient);
        System.out.println("[*] [SuS Cracker v4.1] Applying 43 bypass methods to " + inputJar.getName() + " [Target: " + target.toUpperCase() + "]");
        Map<String, byte[]> jar = readJar(inputJar);
        Map<String, byte[]> out = new LinkedHashMap<>();
        List<String> log = new ArrayList<>();
        int patched = 0;

        for (Map.Entry<String, byte[]> e : jar.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();

            if (!name.endsWith(".class")) {
                if (name.equals("fabric.mod.json") || name.equals("quilt.mod.json")) {
                    bytes = cleanFabricMetadata(bytes);
                    log.add("M29 [Fabric JSON Cleaner]: Removed version deps from " + name);
                    patched++;
                }
                if (name.equals("mods.toml") || name.equals("mcmod.info")) {
                    bytes = cleanForgeMetadata(bytes);
                    log.add("M29 [Forge TOML Cleaner]: Removed version locks from " + name);
                    patched++;
                }
                out.put(name, bytes);
                continue;
            }

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                boolean modified = false;

                for (MethodNode mn : cn.methods) {
                    String mlo = mn.name.toLowerCase();
                    String desc = mn.desc;

                    // Method 01: HWID / UUID Spoof
                    if ((mlo.contains("hwid") || mlo.contains("machineid") || mlo.contains("getuuid")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M01 [HWID]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 02: License / KeyAuth
                    if ((mlo.contains("license") || mlo.contains("checkkey") || mlo.contains("validatekey") || mlo.contains("isauthenticated")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M02 [KeyAuth]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 03: Discord Webhook NOP
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst instanceof String && ((String)ldc.cst).contains("discord.com/api/webhooks")) {
                                ldc.cst = "http://127.0.0.1:9999/sus_dummy";
                                log.add("M03 [Webhook NOP]: " + cn.name + "." + mn.name); patched++; modified = true;
                            }
                        }
                    }
                    // Method 04: Anti-VM
                    if ((mlo.contains("isvm") || mlo.contains("antivm") || mlo.contains("sandbox")) && desc.endsWith("Z")) {
                        forceBool(mn, false); log.add("M04 [Anti-VM]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 05: Anti-Debug
                    if ((mlo.contains("isdebugging") || mlo.contains("antidebug") || mlo.contains("isattached")) && desc.endsWith("Z")) {
                        forceBool(mn, false); log.add("M05 [Anti-Debug]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 06: Meteor Whitelist
                    if ((mlo.contains("checkversion") || mlo.contains("iswhitelisted") || mlo.contains("verifyaddon")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M06 [Meteor Lock]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 07: Time-Bomb
                    if ((mlo.contains("isexpired") || mlo.contains("hasexpired") || mlo.contains("timedout")) && desc.endsWith("Z")) {
                        forceBool(mn, false); log.add("M07 [TimeBomb]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 08: HTTP Status
                    if ((mlo.contains("getresponsecode") || mlo.contains("statuscode")) && desc.endsWith("I")) {
                        forceInt(mn, 200); log.add("M08 [HTTP200]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 09: Hash / Checksum
                    if ((mlo.contains("verifyhash") || mlo.contains("checkintegrity") || mlo.contains("checkchecksum")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M09 [Hash]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 10: Native Stub
                    if (mlo.contains("loadnative") || mlo.contains("checkdll")) {
                        mn.instructions.clear(); mn.instructions.add(new InsnNode(Opcodes.RETURN));
                        log.add("M10 [Native NOP]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 11: VIP/Premium
                    if ((mlo.contains("isvip") || mlo.contains("ispremium") || mlo.contains("ispaid")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M11 [VIP]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 12: Cloud Auth
                    if ((mlo.contains("checkpastebin") || mlo.contains("cloudcheck")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M12 [Cloud]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 13: System.exit defuser
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/System") && min.name.equals("exit")) {
                                mn.instructions.set(insn, new InsnNode(Opcodes.POP));
                                log.add("M13 [Exit Defuse]: " + cn.name + "." + mn.name); patched++; modified = true;
                            }
                        }
                    }
                    // Method 14: IP Blacklist
                    if ((mlo.contains("isblacklisted") || mlo.contains("isbanned") || mlo.contains("isblocked")) && desc.endsWith("Z")) {
                        forceBool(mn, false); log.add("M14 [IP Blacklist]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 15: Discord Role
                    if ((mlo.contains("inguild") || mlo.contains("hasrole") || mlo.contains("isbooster")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M15 [Discord Role]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 16: Java Agent
                    if ((mlo.contains("isagentloaded") || mlo.contains("detectinstrumentation")) && desc.endsWith("Z")) {
                        forceBool(mn, false); log.add("M16 [JavaAgent]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 17: Cosmetics
                    if ((mlo.contains("hascape") || mlo.contains("hascosmetic") || mlo.contains("isunlocked")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M17 [Cosmetic]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 18: Freeze Delay NOP
                    if ((mlo.contains("freezeprotect") || mlo.contains("antitamperdelay")) && desc.endsWith("V")) {
                        mn.instructions.clear(); mn.instructions.add(new InsnNode(Opcodes.RETURN));
                        log.add("M18 [Freeze NOP]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 19: Cert Pinning
                    if ((mlo.contains("verifysignature") || mlo.contains("checkcertificate") || mlo.contains("checkpinner")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M19 [CertPin]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 20: Baritone
                    if ((mlo.contains("isbaritoneallowed") || mlo.contains("allowpathfinding")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M20 [Baritone]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 21: Token Sniffer
                    if (mlo.contains("getsessiontoken") || mlo.contains("grabtoken")) {
                        mn.instructions.clear();
                        mn.instructions.add(new LdcInsnNode("SUS_CRACKER_SECURED"));
                        mn.instructions.add(new InsnNode(Opcodes.ARETURN));
                        log.add("M21 [Token Sniffer]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 22: RSA/AES Interceptor
                    if ((mlo.contains("verifyrsa") || mlo.contains("checkaes") || mlo.contains("validatesignature")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M22 [Crypto]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 23: WebSocket Auth
                    if ((mlo.contains("isauthpacket") || mlo.contains("verifyws") || mlo.contains("isheartbeatvalid")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M23 [WS Auth]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }
                    // Method 24: Config Encryption
                    if ((mlo.contains("isconfigvalid") || mlo.contains("verifysavedkey")) && desc.endsWith("Z")) {
                        forceBool(mn, true); log.add("M24 [Config]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }

                    // Method 26: Pattern-Matching Smart HWID
                    boolean hasHardwareCall = false;
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if ((min.owner.equals("java/net/NetworkInterface") && min.name.equals("getHardwareAddress")) ||
                                (min.owner.equals("java/lang/System") && min.name.equals("getenv")) ||
                                (min.owner.equals("java/util/UUID") && min.name.equals("randomUUID"))) {
                                hasHardwareCall = true;
                            }
                        }
                    }
                    if (hasHardwareCall && desc.endsWith("Z")) {
                        forceBool(mn, true);
                        log.add("M26 [Smart HWID Pattern]: " + cn.name + "." + mn.name); patched++; modified = true;
                    }

                    // Method 28: Module Force-Enable via Branch Reversal
                    if (mlo.equals("isactive") || mlo.equals("canenable") || mlo.equals("isenabled")) {
                        boolean reversed = false;
                        for (AbstractInsnNode insn : mn.instructions.toArray()) {
                            if (insn instanceof JumpInsnNode) {
                                JumpInsnNode ji = (JumpInsnNode) insn;
                                if (ji.getOpcode() == Opcodes.IFEQ) {
                                    mn.instructions.set(insn, new JumpInsnNode(Opcodes.IFNE, ji.label));
                                    reversed = true;
                                } else if (ji.getOpcode() == Opcodes.IFNE) {
                                    mn.instructions.set(insn, new JumpInsnNode(Opcodes.IFEQ, ji.label));
                                    reversed = true;
                                }
                            }
                        }
                        if (reversed) {
                            log.add("M28 [Module Force-Enable]: Branch reversed in " + cn.name + "." + mn.name);
                            patched++; modified = true;
                        }
                    }

                    // Method 30: KeyAuth / Custom API JSON Mock Injector
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst instanceof String) {
                                String sv = (String) ldc.cst;
                                if (sv.contains("keyauth.win") || sv.contains("keyauth.xyz") ||
                                    sv.contains("api.aternos.org") || (sv.startsWith("http") && sv.contains("/api/license"))) {
                                    ldc.cst = "http://127.0.0.1:7777/mock_license";
                                    log.add("M30 [KeyAuth Mock]: Redirected license API in " + cn.name + "." + mn.name);
                                    patched++; modified = true;
                                }
                            }
                        }
                    }

                    // Method 31: Reflection-based Auth Neutralizer
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/reflect/Method") && min.name.equals("invoke")) {
                                AbstractInsnNode prev = insn.getPrevious();
                                if (prev != null) {
                                    InsnList patch = new InsnList();
                                    patch.add(new InsnNode(Opcodes.POP2));
                                    patch.add(new InsnNode(Opcodes.POP));
                                    patch.add(new InsnNode(Opcodes.ICONST_1));
                                    patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                        "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                                    mn.instructions.insertBefore(insn, patch);
                                    mn.instructions.remove(insn);
                                    log.add("M31 [Reflect Auth]: Neutralized Method.invoke in " + cn.name + "." + mn.name);
                                    patched++; modified = true;
                                }
                            }
                        }
                    }

                    // Method 33: Scheduler / Timer License Killer
                    if (mlo.contains("scheduleatfixedrate") || mlo.contains("schedulewithfixeddelay")) {
                        mn.instructions.clear();
                        mn.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                        mn.instructions.add(new InsnNode(Opcodes.ARETURN));
                        log.add("M33 [Timer License Killer]: Defused scheduler in " + cn.name + "." + mn.name);
                        patched++; modified = true;
                    }

                    // Method 34: StackTrace / Decompile Detection Bypass
                    if (mlo.contains("getstacktrace") || mlo.contains("isdecompiled")) {
                        mn.instructions.clear();
                        mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
                        mn.instructions.add(new InsnNode(Opcodes.IRETURN));
                        log.add("M34 [StackTrace Detection Bypass]: " + cn.name + "." + mn.name);
                        patched++; modified = true;
                    }

                    // Method 35: Obfuscated Class Name Heuristic Auth Cracker
                    if ((cn.name.length() <= 3 || cn.name.startsWith("\u200B")) && desc.endsWith("Z") && mn.instructions.size() < 15) {
                        forceBool(mn, true);
                        log.add("M35 [Heuristic Auth Cracker]: Force-true on " + cn.name + "." + mn.name);
                        patched++; modified = true;
                    }

                    // Method 25: Watermark & Branding String Stripper
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst instanceof String) {
                                String sv = ((String)ldc.cst).toLowerCase();
                                if (sv.contains("watermark") || sv.contains("licensed to") || sv.contains("cracked by") ||
                                    sv.contains("protected by sus") || sv.contains("sus cracker") ||
                                    sv.contains("keyauth.win") || sv.contains("keyauth.xyz") ||
                                    (sv.startsWith("hwid:") && sv.length() < 80)) {
                                    ldc.cst = "";
                                    log.add("M25 [Watermark Strip]: Cleared watermark string in " + cn.name + "." + mn.name);
                                    patched++; modified = true;
                                }
                            }
                        }
                    }

                    // Method 36: Meteor Module Extended Bypass (module name pattern matching)
                    if (isMeteor || isAny) {
                        String fullSig = cn.name + "." + mn.name;
                        if ((mlo.contains("isready") || mlo.contains("isenabled") || mlo.equals("active") ||
                             mlo.contains("canenable") || mlo.contains("isloaded") ||
                             mlo.contains("ismoduleactive") || mlo.contains("isallowed")) && desc.endsWith("Z")) {
                            if (!mlo.contains("test") && !mlo.contains("network")) {
                                forceBool(mn, true);
                                log.add("M36 [Meteor Module Extended]: Force-enabled in " + fullSig);
                                patched++; modified = true;
                            }
                        }
                    }

                    // Method 37: Client Auth Bypass (Lunar / Badlion / LabyMod / Impact)
                    if (isClient || isAny) {
                        if ((mlo.contains("islunarpremium") || mlo.contains("isbadlion") || mlo.contains("islabyplus") ||
                             mlo.contains("isimpactuser") || mlo.contains("ispremiumuser") ||
                             mlo.contains("isranked") || mlo.contains("hasbadge") || mlo.contains("issubscriber")) && desc.endsWith("Z")) {
                            forceBool(mn, true);
                            log.add("M37 [Client Premium Bypass]: Force-true in " + cn.name + "." + mn.name);
                            patched++; modified = true;
                        }
                    }

                    // Method 38: Forge Version Lock & Dependency Check Bypass
                    if (isForge || isAny) {
                        if ((mlo.contains("verifyforgeversion") || mlo.contains("iscompatibleforgeversion") ||
                             mlo.contains("checkmoddependency") || mlo.contains("isfordependencysatisfied")) && desc.endsWith("Z")) {
                            forceBool(mn, true);
                            log.add("M38 [Forge Version Lock Bypass]: Force-compatible in " + cn.name + "." + mn.name);
                            patched++; modified = true;
                        }
                    }

                    // Method 39: RuntimeExec & ProcessBuilder Deep Defuser (all variants)
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if ((min.owner.equals("java/lang/Runtime") && (min.name.equals("exec") || min.name.equals("halt"))) ||
                                (min.owner.equals("java/lang/ProcessBuilder") && min.name.equals("start")) ||
                                (min.owner.equals("java/lang/System") && min.name.equals("halt"))) {
                                // Replace with null push (handles both void and Object returns)
                                mn.instructions.set(insn, new InsnNode(Opcodes.ACONST_NULL));
                                log.add("M39 [RuntimeExec Defuse]: Blocked process call in " + cn.name + "." + mn.name);
                                patched++; modified = true;
                            }
                        }
                    }

                    // Method 40: Class.forName Dynamic Auth Bypass
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/Class") && min.name.equals("forName")) {
                                AbstractInsnNode prev = insn.getPrevious();
                                if (prev instanceof LdcInsnNode) {
                                    LdcInsnNode ldcPrev = (LdcInsnNode) prev;
                                    if (ldcPrev.cst instanceof String) {
                                        String cls = ((String) ldcPrev.cst).toLowerCase();
                                        if (cls.contains("auth") || cls.contains("license") || cls.contains("guard") || cls.contains("protect")) {
                                            ldcPrev.cst = "java.lang.Object";
                                            log.add("M40 [Class.forName Auth]: Redirected dynamic class load in " + cn.name + "." + mn.name);
                                            patched++; modified = true;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Method 41: ThreadLocal Auth State Bypass
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.owner.equals("java/lang/ThreadLocal") && min.name.equals("get")) {
                                if (mlo.contains("auth") || mlo.contains("license") || mlo.contains("session") ||
                                    mlo.contains("valid") || mlo.contains("check")) {
                                    InsnList patch = new InsnList();
                                    patch.add(new InsnNode(Opcodes.POP));
                                    patch.add(new InsnNode(Opcodes.ICONST_1));
                                    patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                                    mn.instructions.insertBefore(insn, patch);
                                    mn.instructions.remove(insn);
                                    log.add("M41 [ThreadLocal Auth]: Injected true state in " + cn.name + "." + mn.name);
                                    patched++; modified = true;
                                }
                            }
                        }
                    }

                    // Method 42: String-Based URL Auth Endpoint Redirector (broad scan)
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst instanceof String) {
                                String sv = (String) ldc.cst;
                                String svl = sv.toLowerCase();
                                if (sv.startsWith("http") && (svl.contains("/auth") || svl.contains("/license") ||
                                    svl.contains("/verify") || svl.contains("/check") || svl.contains("/validate") ||
                                    svl.contains("/session") || svl.contains("/login") || svl.contains("/premium"))) {
                                    ldc.cst = "http://127.0.0.1:7777/sus_mock_auth";
                                    log.add("M42 [Endpoint Redir]: Redirected auth URL in " + cn.name + "." + mn.name);
                                    patched++; modified = true;
                                }
                            }
                        }
                    }
                    // Method 43: Annotation-Based Auth Stripper (@License @Protected @Premium)
                    if (mn.visibleAnnotations != null) {
                        boolean hadAuthAnnotation = mn.visibleAnnotations.removeIf(an ->
                            an.desc != null && (
                                an.desc.toLowerCase().contains("license") ||
                                an.desc.toLowerCase().contains("protect") ||
                                an.desc.toLowerCase().contains("premium") ||
                                an.desc.toLowerCase().contains("authorized")
                            )
                        );
                        if (hadAuthAnnotation) {
                            log.add("M43 [Annotation Strip]: Removed auth annotation from " + cn.name + "." + mn.name);
                            patched++; modified = true;
                        }
                    }
                }

                // Method 27: Fabric Mixin Auth-Hook Stripper
                if (cn.visibleAnnotations != null) {
                    cn.visibleAnnotations.removeIf(an -> {
                        if (an.desc != null && an.desc.contains("Mixin")) {
                            for (MethodNode m : cn.methods) {
                                String ml = m.name.toLowerCase();
                                if (ml.contains("auth") || ml.contains("license") || ml.contains("hwid") || ml.contains("key")) {
                                    log.add("M27 [Mixin Auth Hook]: Stripped Mixin annotation from " + cn.name);
                                    return true;
                                }
                            }
                        }
                        return false;
                    });
                }

                // Method 32: Static Auth-Flag Field Force-Patcher
                for (FieldNode fn : cn.fields) {
                    String fnLower = fn.name.toLowerCase();
                    if ((fn.access & Opcodes.ACC_STATIC) != 0 && (fn.desc.equals("Z") || fn.desc.equals("I"))) {
                        if (fnLower.contains("license") || fnLower.contains("auth") || fnLower.contains("vip") || fnLower.contains("valid")) {
                            fn.value = fn.desc.equals("Z") ? true : 1;
                            log.add("M32 [Static Field Force-Patcher]: Set " + cn.name + "." + fn.name + " = true");
                            patched++; modified = true;
                        }
                    }
                }

                if (modified) {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    cn.accept(cw);
                    out.put(name, cw.toByteArray());
                } else {
                    out.put(name, bytes);
                }
            } catch (Exception ex) {
                out.put(name, bytes);
            }
        }

        writeJar(outputJar, out);
        System.out.println("[+] [SuS Cracker v4.1] Completed!");
        System.out.println("    Target Type     : " + target.toUpperCase());
        System.out.println("    Patches Applied : " + patched);
        System.out.println("    Classes Scanned : " + out.size());
        System.out.println("    Output File     : " + outputJar.getAbsolutePath());
        log.forEach(l -> System.out.println("  * " + l));
    }

    private static byte[] cleanFabricMetadata(byte[] src) {
        try {
            String json = new String(src, StandardCharsets.UTF_8);
            json = json.replaceAll("\"depends\"\\s*:\\s*\\{[^}]+}", "\"depends\": {}");
            json = json.replaceAll("\"breaks\"\\s*:\\s*\\{[^}]+}", "\"breaks\": {}");
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return src;
        }
    }

    private static byte[] cleanForgeMetadata(byte[] src) {
        try {
            String toml = new String(src, StandardCharsets.UTF_8);
            toml = toml.replaceAll("\\[\\[dependencies\\.[^]]+]]([^\\[]*)", "");
            return toml.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return src;
        }
    }

    private static void forceBool(MethodNode mn, boolean v) {
        mn.instructions.clear();
        mn.instructions.add(new InsnNode(v ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        mn.instructions.add(new InsnNode(Opcodes.IRETURN));
    }

    private static void forceInt(MethodNode mn, int v) {
        mn.instructions.clear();
        mn.instructions.add(new IntInsnNode(Opcodes.SIPUSH, v));
        mn.instructions.add(new InsnNode(Opcodes.IRETURN));
    }

    private static Integer getConstant(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
        if (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) return ((IntInsnNode) insn).operand;
        if (op == Opcodes.LDC) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof Integer) return (Integer) cst;
        }
        return null;
    }

    static class SafeClassWriter extends ClassWriter {
        public SafeClassWriter(int flags) {
            super(flags);
        }
        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Throwable t) {
                return "java/lang/Object";
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════
    private static Map<String, byte[]> readJar(File f) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(f)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                try (InputStream is = zf.getInputStream(entry)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int n;
                    while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
                    byte[] data = baos.toByteArray();
                    m.put(entry.getName(), data);

                    if (entry.getName().endsWith(".jar") || entry.getName().endsWith(".zip")) {
                        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
                            ZipEntry inner;
                            while ((inner = zis.getNextEntry()) != null) {
                                if (inner.isDirectory()) continue;
                                ByteArrayOutputStream innerBaos = new ByteArrayOutputStream();
                                int inN;
                                while ((inN = zis.read(buf)) > 0) innerBaos.write(buf, 0, inN);
                                m.put("unpacked_nested/" + inner.getName(), innerBaos.toByteArray());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return m;
    }

    private static void writeJar(File out, Map<String, byte[]> entries) throws IOException {
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
    }

    private static void zipDirectory(File src, File zipOut) throws IOException {
        if (zipOut.getParentFile() != null) zipOut.getParentFile().mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut))) {
            zipFolder(src, src, zos);
        }
    }

    private static void zipFolder(File root, File cur, ZipOutputStream zos) throws IOException {
        File[] files = cur.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { zipFolder(root, f, zos); continue; }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            zos.putNextEntry(new ZipEntry(rel));
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    private static void deleteDir(File dir) {
        if (dir.isDirectory()) { File[] ch = dir.listFiles(); if (ch != null) for (File c : ch) deleteDir(c); }
        dir.delete();
    }
}
