package dev.skidfuscator.obfuscator.transform.impl.j2c;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.FinalClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.method.FinalMethodTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.skid.FinalSkidTransformEvent;
import dev.skidfuscator.obfuscator.predicate.cache.CacheTemplate;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.skidasm.SkidMethodNode;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidControlFlowGraph;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import org.mapleir.asm.ClassHelper;
import org.mapleir.asm.ClassNode;
import org.mapleir.ir.code.expr.invoke.DynamicInvocationExpr;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.ArrayList;

public class NativeTransformer extends AbstractTransformer {
    public NativeTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Native");
    }

    public static boolean DEBUG = false;
    public static Class<?> DISPATCHER = Dispatcher.class;
    public static Class<?> CACHE = CacheTemplate.class;

    @Listen
    void handle(final FinalSkidTransformEvent event) {
        final SkidClassNode dispatcher;
        try {
             dispatcher = DEBUG
                     ? new SkidClassNode(
                             ClassHelper.create(DISPATCHER).node,
                             skidfuscator
                     )
                     : new SkidClassNode(
                             ClassHelper.create(DispatcherDump.dump(), 0).node,
                             skidfuscator
                     );
             dispatcher.node.name = "skid/Dispatcher";
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
                                "skid/Dispatcher.class",
                                dispatcher.toByteArray(),
                                dispatcher
                        )
                );
    }

    void handle(final FinalClassTransformEvent event) {
        final SkidClassNode classNode = event.getClassNode();
        final SkidMethodNode clinit = classNode.getClassInit();
        clinit.getGroup().setName("$clinit");
        final SkidMethodNode proxy = classNode
                .createMethod()
                .name("<clinit>")
                .access(Opcodes.ACC_STATIC)
                .desc("()V")
                .phantom(true)
                .build();

        final SkidControlFlowGraph cfg = proxy.getCfg();
    }

    @Listen
    void handle(final FinalMethodTransformEvent event) {
        final SkidMethodNode methodNode = event.getMethodNode();

        if (methodNode.isClinit()
                || methodNode.isInit()
                || methodNode.isAbstract()
                || methodNode.isNative()
                || methodNode.isSynthetic()) {
            return;
        }

        final SkidControlFlowGraph cfg = methodNode.getCfg();

        if (cfg.allExprStream().anyMatch(DynamicInvocationExpr.class::isInstance))
            return;

        if (true)
            return;

        if (methodNode.node.invisibleAnnotations == null) {
            methodNode.node.invisibleAnnotations = new ArrayList<>();
        }

        methodNode.node.invisibleAnnotations.add(
                new AnnotationNode("Ldev/skidfuscator/annotations/NativeObfuscation;")
        );
    }
}
