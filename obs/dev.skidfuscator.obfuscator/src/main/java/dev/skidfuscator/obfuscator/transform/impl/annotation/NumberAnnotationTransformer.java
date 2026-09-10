package dev.skidfuscator.obfuscator.transform.impl.annotation;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.RunClassTransformEvent;
import dev.skidfuscator.obfuscator.number.NumberManager;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.*;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.skidasm.fake.FakeArithmeticExpr;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.StaticInvocationExpr;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.stream.Collectors;

public class NumberAnnotationTransformer extends AbstractTransformer {
    private final Map<SkidGroup, Integer> opaqueMap = new HashMap<>();

    public NumberAnnotationTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Int Annotation Encryption");
    }
    @Listen
    void handle(final RunClassTransformEvent event) {
        final SkidClassNode classNode = event.getClassNode();
        final Skidfuscator skidfuscator = event.getSkidfuscator();

        if (!classNode.isAnnotation())
            return;

        final Map<String, Integer> calls = new HashMap<>();
        for (MethodNode method : classNode.getMethods()) {
            final Type returnType = Type.getReturnType(method.getDesc());

            if (!returnType.equals(Type.BYTE_TYPE)
                    && !returnType.equals(Type.SHORT_TYPE)
                    && !returnType.equals(Type.INT_TYPE))
                continue;

            final SkidMethodNode skidMethodNode = (SkidMethodNode) method;
            final SkidGroup group = skidfuscator
                    .getHierarchy()
                    .getGroup(skidMethodNode);

            int key = opaqueMap.computeIfAbsent(group, e -> RandomUtil.nextInt());
            calls.put(method.getName(), key);

            for (SkidInvocation invoker : group.getInvokers()) {
                final int value;

                if (invoker.getOwner() instanceof SkidMethodNode) {
                    value = ((SkidMethodNode) invoker.getOwner())
                            .getBlockPredicate((SkidBlock) invoker.asExpr().getBlock());
                } else {
                    value = RandomUtil.nextInt();
                }

                final Expr mutate = NumberManager.encrypt(
                        key,
                        value,
                        invoker.asExpr().getBlock(),
                        new PredicateFlowGetter() {
                            @Override
                            public Expr get(final BasicBlock cfg) {
                                if (invoker.getOwner() instanceof SkidMethodNode) {
                                    return ((SkidMethodNode) invoker.getOwner())
                                            .getFlowPredicate()
                                            .getGetter()
                                            .get(invoker.asExpr().getBlock());
                                } else {
                                    return new ConstantExpr(value, Type.INT_TYPE);
                                }
                            }
                        }
                );

                final Expr bogus = new ConstantExpr(69);
                final ArithmeticExpr modified = new FakeArithmeticExpr(
                        bogus,
                        mutate,
                        ArithmeticExpr.Operator.XOR
                );

                final Expr expr = (Expr) invoker.getExpr();
                expr.getParent().overwrite(expr, modified);
                expr.setParent(null);
                modified.setLeft(expr);
            }
        }

        final List<SkidAnnotation> nodes = skidfuscator
                .getHierarchy()
                .getAnnotations(classNode);

        if (nodes == null)
            return;

        for (SkidAnnotation node : nodes) {
            calls.forEach((name, key) -> {
                final SkidAnnotation.AnnotationValue<Integer> value = node.getValue(name);

                if (value == null) {
                    /*Skidfuscator.LOGGER.warn("Value of annotation " + node.getNode().desc
                            + " (asm: " + (node.getNode().values == null ? "null values" : Arrays.toString(node.getNode().values.toArray(new Object[0]))) + ") "
                            + " could not find value " + value
                            + "\n\nstack:\n  "
                            + node.getValues().entrySet().stream().map(e -> e.getKey() + " --> " + e.getValue().get()).collect(Collectors.joining("\n  "))
                    );*/
                    return;
                }

                final SkidGroup group = skidfuscator.getHierarchy().getGroup((SkidMethodNode) value.getMethodNode());
                value.set(value.get() ^ opaqueMap.get(group));
            });
        }
    }
}
