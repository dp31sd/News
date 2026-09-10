package dev.skidfuscator.obfuscator.transform.impl.annotation;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.RunClassTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.*;
import dev.skidfuscator.obfuscator.skidasm.expr.SkidConstantExpr;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.Transformer;
import dev.skidfuscator.obfuscator.transform.impl.string.generator.BytesEncryptionGenerator;
import dev.skidfuscator.obfuscator.transform.impl.string.generator.EncryptionGenerator;
import dev.skidfuscator.obfuscator.transform.impl.string.generator.basic.BasicEncryptionGenerator;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import dev.skidfuscator.obfuscator.util.TypeUtil;
import dev.skidfuscator.obfuscator.util.misc.Pair;
import dev.skidfuscator.obfuscator.util.misc.Parameter;
import org.mapleir.asm.ClassNode;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.StaticInvocationExpr;
import org.objectweb.asm.Type;

import java.util.*;

public class StringAnnotationTransformer extends AbstractTransformer {
    private final Map<SkidGroup, Pair<String, Integer>> opaqueMap = new HashMap<>();
    private final Map<SkidGroup, EncryptionGenerator> keyAnnotationMap = new HashMap<>();

    private final Set<ClassNode> INJECTED_ANNOTATION = new HashSet<>();

    public StringAnnotationTransformer(Skidfuscator skidfuscator) {
        this(skidfuscator, Collections.emptyList());
    }

    public StringAnnotationTransformer(Skidfuscator skidfuscator, List<Transformer> children) {
        super(skidfuscator, "String Annotation Encryption", children);
    }

    @Listen
    void handle(final RunClassTransformEvent event) {
        final SkidClassNode methodNode = event.getClassNode();
        final Skidfuscator skidfuscator = event.getSkidfuscator();

        /*
         * Only run through annotation classes because we're basically doing
         *
         * annotation -> find callers, find values, obf
         */
        if (!methodNode.isAnnotation())
            return;

        final Map<String, Pair<String, Integer>> calls = new HashMap<>();

        /*
         * Iterate through every method in the annotation class since we know
         * these correspond to the annotation fields. In this case, we're only
         * looking for STRING_TYPE
         */
        for (MethodNode method : methodNode.getMethods()) {
            final Type returnType = Type.getReturnType(method.getDesc());

            if (!returnType.equals(TypeUtil.STRING_TYPE))
                continue;

            final SkidMethodNode skidMethodNode = (SkidMethodNode) method;

            /*
             * Get the group of the method (since it can be inherited from
             * another annotation, then store a key and a method name for
             * that specific method)
             *
             * In a nutshell,
             *
             * AnnotationClass:
             *      methodA -> Key #1, data encrypted with key #1 & keyset #1
             *      methodB -> Key #2, data encrypted with key #2 & keyset #2
             */
            final SkidGroup group = skidfuscator
                    .getHierarchy()
                    .getGroup(skidMethodNode);

            final Parameter parameter = new Parameter(method.getDesc());
            parameter.setReturnArg(TypeUtil.BYTE_ARRAY_TYPE);
            group.setDesc(parameter.getDesc());

            final Pair<String, Integer> key = opaqueMap.computeIfAbsent(
                    group,
                    e -> new Pair<>(
                            RandomUtil.randomIsoString(26),
                            RandomUtil.nextInt()
                    )
            );
            calls.put(method.getName(), key);

            final EncryptionGenerator generator = keyAnnotationMap.computeIfAbsent(group, e -> {
                final int size = RandomUtil.nextInt(127) + 1;
                final Integer[] keysT = new Integer[size];
                for (int i = 0; i < size; i++) {
                    keysT[i] = RandomUtil.nextInt(127);
                }

                return new BytesEncryptionGenerator(keysT);
            });

            if (method.node.annotationDefault != null) {
                method.node.annotationDefault = generator.encrypt((String) method.node.annotationDefault, key.getB());
            }

            /*
             * Iterate the callers of the method and override these with an invocation
             * to the decoder. If the decoder is absent, create it in the class (this
             * needs to change)
             *
             * Before:  annotation.valueA()
             * After:   decrypt(annotation.valueA(), key)
             *
             * Thanks the NumberTransformer, it can take the form of
             *
             * After: decrypt(annotation.valueA(), decryptPredicate(encryptedKey, predicate))
             */
            for (SkidInvocation invoker : group.getInvokers()) {
                // Not necessary as it's covered by the number obf. Still pretty weak tho
                /*
                final int value;
                if (invoker.getOwner() instanceof SkidMethodNode) {
                    value = ((SkidMethodNode) invoker.getOwner())
                            .getBlockPredicate((SkidBlock) invoker.getExpr().getBlock());
                } else {
                    value = RandomUtil.nextInt();
                }

                final Expr mutate = NumberManager.encrypt(
                        key.getB(),
                        value,
                        () -> {
                            if (invoker.getOwner() instanceof SkidMethodNode) {
                                return ((SkidMethodNode) invoker.getOwner())
                                        .getFlowPredicate()
                                        .getGetter()
                                        .get();
                            } else {
                                return new ConstantExpr(value, Type.INT_TYPE);
                            }
                        }
                );*/

                if (!INJECTED_ANNOTATION.contains(invoker.getOwner().owner)) {
                    generator.visit(
                            (SkidClassNode) invoker.getOwner().owner,
                            key.getA()
                    );
                    INJECTED_ANNOTATION.add(invoker.getOwner().owner);
                }

                /*
                 * If you're curious about the null, ignore it, it's a quick hack to be able
                 * to initialize the static invocation without calling the invoker expression
                 * as it is still attached to the parent.
                 */
                final StaticInvocationExpr modified = new StaticInvocationExpr(
                        new Expr[]{
                                new ConstantExpr("A"),
                                new ConstantExpr(key.getB(), Type.INT_TYPE)
                        },
                        invoker.getOwner().getOwner(),
                        key.getA(),
                        "([BI)Ljava/lang/String;"
                );

                /*
                 * Overwrite the expression and properly correct it with the invoker
                 * expression. This should work universally with any scenario.
                 */
                invoker.asExpr().getParent().overwrite(invoker.asExpr(), modified);
                modified.getArgumentExprs()[0] = invoker.asExpr();
            }

            INJECTED_ANNOTATION.clear();
        }

        /*
         * Get all the implementations of this annotation class, if there
         * are none, the hierarchy will return null (and not new ArrayList
         * because that'd be gae)
         */
        final List<SkidAnnotation> nodes = skidfuscator
                .getHierarchy()
                .getAnnotations(methodNode);

        if (nodes == null)
            return;

        /*
         * For each of the annotation, we check what values we have modified
         * and we appropriately allocate the proper encrypted value by grabbing
         * the key and the keyset allocated to the group
         *
         * Hence:
         *
         * Before:  @Annotation(name1="value1")
         * After:   @Annotation(name1="encryptedValue1")
         */
        for (SkidAnnotation node : nodes) {
            calls.forEach((name, key) -> {
                final SkidAnnotation.AnnotationValue<String> value = node.getValue(name);

                if (value == null) {
                    //System.out.println("Cannot find annotation method equivalent of value name " + name);
                    return;
                }

                final SkidGroup group = skidfuscator.getHierarchy().getGroup(value.getMethodNode());

                final EncryptionGenerator generator = keyAnnotationMap.get(group);
                final byte[] encrypted = generator.encrypt(value.get(), key.getB());
                node.setValue(value.getName(), encrypted);
            });
        }

    }
}
