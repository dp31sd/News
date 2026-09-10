package dev.skidfuscator.obfuscator.transform.impl.reference;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.EventPriority;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.method.FinalMethodTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.method.RunMethodTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.skid.FinalSkidTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.skidasm.SkidMethodNode;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidControlFlowGraph;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.impl.flow.exceptreturn.ExceptionReturnConfig;
import dev.skidfuscator.obfuscator.transform.impl.j2c.Dispatcher;
import dev.skidfuscator.obfuscator.transform.impl.j2c.DispatcherDump;
import dev.skidfuscator.obfuscator.transform.impl.j2c.NativeTransformer;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import dev.skidfuscator.obfuscator.util.misc.Parameter;
import org.mapleir.asm.ClassHelper;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.CastExpr;
import org.mapleir.ir.code.expr.invoke.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.HashSet;
import java.util.LinkedList;

public class ReferenceTransformer extends AbstractTransformer {
    public ReferenceTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Reference");
    }

    public static Class<?> REF_DISPATCHER = ReferenceDispatcher.class;

    @Listen(EventPriority.FINALIZER)
    void handle(final FinalSkidTransformEvent event) {
        final SkidClassNode dispatcher;
        try {
            dispatcher = NativeTransformer.DEBUG
                    ? new SkidClassNode(
                    ClassHelper.create(REF_DISPATCHER).node,
                    skidfuscator
            )
                    : new SkidClassNode(
                    ClassHelper.create(ReferenceDispatcherDump.dump(), 0).node,
                    skidfuscator
            );
        } catch (Exception e) {
            Skidfuscator.LOGGER.error("Failed to create dispatcher. This WILL cause issues!", e);
            return;
        }

        skidfuscator.getClassSource().add(dispatcher);
        skidfuscator
                .getJarContents()
                .getClassContents()
                .add(
                        new JarClassData(
                                "skid/Ref.class",
                                dispatcher.toByteArray(),
                                dispatcher
                        )
                );
        skidfuscator.getClassRemapper().add(
                Type.getObjectType(dispatcher.getName()).getInternalName(),
                "skid/Ref"
        );
    }

    @Listen(EventPriority.HIGHEST)
    void handle(final FinalMethodTransformEvent event) {
        final Skidfuscator skidfuscator = event.getSkidfuscator();
        final SkidMethodNode methodNode = event.getMethodNode();
        final SkidClassNode classNode = methodNode.getParent();

        if (classNode.isEnum() || classNode.isAbstract() || classNode.isAnnotation() || !classNode.isPublic())
            return;

        if (classNode.node.signature != null && !classNode.node.signature.equals(""))
            return;

        if (methodNode.owner.node.name.startsWith("[L")
                || methodNode.isClinit()
                || methodNode.isInit())
            return;

        final SkidControlFlowGraph cfg = methodNode.getCfg();


        for (BasicBlock vertex : cfg.vertices()) {
            for (Stmt stmt : new LinkedList<>(vertex)) {
                for (Expr expr : stmt.enumerateOnlyChildren()) {
                    final boolean compat = expr instanceof VirtualInvocationExpr
                            || expr instanceof StaticInvocationExpr;

                    if (!compat || expr.getParent() == null)
                        continue;

                    final InvocationExpr e = (InvocationExpr) expr;

                    if (e.getName().equals("<init>")
                            || e.getCallType() == InvocationExpr.CallType.INTERFACE)
                        return;

                    //System.out.println(expr);

                    final Expr[] args = new Expr[new Parameter(e.getDesc()).getArgs().size() + (e.isStatic() ? 0 : 1)];

                    for (int i = 0; i < args.length; i++) {
                        final Expr arg = e.getArgumentExprs()[i];
                        assert arg != null : "Failed for null arg " + e.toString() + " of desc " + e.getDesc();
                        args[i] = arg;
                    }

                    for (int i = 0; i < args.length; i++) {
                        args[i].unlink();
                    }

                    final String dex = e.getDesc();

                    /* Stolen from Caesium */
                    String newSig = e.getCallType().getOpcode() == Opcodes.INVOKESTATIC
                            ? dex
                            : dex.replace("(", "(Ljava/lang/Object;");

                    /*
                     * Original return type fixing
                     */
                    Type origReturnType = Type.getReturnType(newSig);
                    returnType: {
                        final boolean isObject = origReturnType.getSort() == Type.OBJECT;
                        final boolean isArray = origReturnType.getSort() == Type.ARRAY
                                && origReturnType.getElementType().getSort() == Type.OBJECT;
                        if (!isObject && !isArray) {
                            break returnType;
                        }

                        final String mapped = skidfuscator
                                .getClassRemapper()
                                .map(isObject
                                        ? origReturnType.getInternalName()
                                        : origReturnType.getElementType().getInternalName()
                                        );

                        if (mapped == null)
                            break returnType;

                        origReturnType = Type.getObjectType(isObject ? mapped : "[" + mapped);
                        System.out.println("return " + mapped + " from " + origReturnType.getInternalName());
                    }

                    /*
                     * Argument type fixing
                     */
                    Type[] argsv = Type.getArgumentTypes(newSig);
                    for (int i = 0; i < argsv.length; i++) {
                        Type type = argsv[i];

                        argsv[i] = type.getSort() == Type.OBJECT ? Type.getType(Object.class) : type;
                    }

                    newSig = Type.getMethodDescriptor(origReturnType, argsv);
                    /* end of stealing */

                    /*
                     * Owner value fixing
                     */
                    String owner = e.getOwner();
                    Type type = Type.getObjectType(owner);
                    owner = skidfuscator
                            .getClassRemapper()
                            .mapOrDefault(type.getInternalName());
                    owner = owner.replace("/", ".");

                    /*
                     * Parameter description fixing
                     */
                    Parameter desc = new Parameter(e.getDesc());
                    Parameter descMapped = new Parameter(e.getDesc());
                    descMapped.getArgs().clear();
                    for (Type arg : desc.getArgs()) {
                        final boolean isObject = origReturnType.getSort() == Type.OBJECT;
                        final boolean isArray = origReturnType.getSort() == Type.ARRAY
                                && origReturnType.getElementType().getSort() == Type.OBJECT;
                        if (!isObject && !isArray) {
                            descMapped.addParameter(arg);
                            continue;
                        }

                        final String mapped = skidfuscator
                                .getClassRemapper()
                                .map(arg.getInternalName());

                        if (mapped == null) {
                            descMapped.addParameter(arg);
                            continue;
                        }

                        final Type remappedType = Type.getObjectType(isObject ? mapped : "[" + mapped);
                        System.out.println("Mapped " + arg + " to " + remappedType);
                        descMapped.addParameter(remappedType);
                    }

                    assert descMapped.computeSize() == desc.computeSize() : "Failed equivalent sizes";

                    returnArg: {
                        if (descMapped.getReturnType().getSort() != Type.OBJECT) {
                            break returnArg;
                        }

                        final String mapped = skidfuscator
                                .getClassRemapper()
                                .map(descMapped.getReturnType().getInternalName());

                        if (mapped == null) {
                            break returnArg;
                        }

                        descMapped.setReturnArg(Type.getObjectType(mapped));
                    }

                    /*
                     * Creation of the bootstrapper
                     */
                    final DynamicInvocationExpr replace = new DynamicInvocationExpr(
                            new Handle(
                                    Opcodes.H_INVOKESTATIC,
                                    "skid/Ref",
                                    "dispatch",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                    false
                            ),
                            new Object[]{
                                    e.getCallType().getOpcode(),
                                    owner,
                                    e.getName(),
                                    descMapped.getDesc()
                            },
                            newSig,
                            args,
                            RandomUtil.randomAlphabeticalString(16)
                    );

                    System.out.println(replace.toString());

                    final CodeUnit parent = e.getParent();
                    //e.unlink();

                    Expr rep = replace;

                    if (origReturnType.getSort() != Type.VOID) {
                        rep = new CastExpr(rep, origReturnType);
                    }
                    parent.overwrite(e, rep);
                }
            }
        }
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new ReferenceConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public ReferenceConfig getConfig() {
        return (ReferenceConfig) super.getConfig();
    }
}
