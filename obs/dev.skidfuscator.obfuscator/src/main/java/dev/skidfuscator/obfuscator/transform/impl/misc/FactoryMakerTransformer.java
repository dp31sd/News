package dev.skidfuscator.obfuscator.transform.impl.misc;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.FinalClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.InitClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.PreClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.RunClassTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.skidasm.SkidInvocation;
import dev.skidfuscator.obfuscator.skidasm.SkidMethodNode;
import dev.skidfuscator.obfuscator.skidasm.builder.SkidClassNodeBuilder;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import dev.skidfuscator.obfuscator.util.misc.Parameter;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.AllocObjectExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.StaticInvocationExpr;
import org.mapleir.ir.code.stmt.ReturnStmt;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class FactoryMakerTransformer extends AbstractTransformer {
    public FactoryMakerTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Flow Factory Maker");
    }

    @Listen
    void handle(final FinalClassTransformEvent event) {
        final Skidfuscator skidfuscator = event.getSkidfuscator();
        final SkidClassNode classNode = event.getClassNode();

        if (classNode.isEnum() || classNode.isAbstract() || classNode.isAnnotation() || !classNode.isPublic())
            return;

        if (classNode.node.signature != null && !classNode.node.signature.equals(""))
            return;

        classNode.getMethods()
                .stream()
                .filter(e -> e.getName().equals("<init>"))
                .forEach(method -> {
                    if (method.node.signature != null)
                        return;

                    final Set<SkidInvocation> skidInvocations = skidfuscator
                            .getHierarchy()
                            .getInvokers(method);

                    if (skidInvocations != null) {
                        final Parameter parameter = new Parameter(method.getDesc());
                        parameter.setReturnArg(Type.getObjectType(method.getOwner()));
                        final String desc = parameter.getDesc();

                        final SkidMethodNode methodNode = skidfuscator.getFactoryNode()
                                .createMethod()
                                //.name(classNode.getName().replace("/", "_") + "_ini_" + RandomUtil.randomIsoString(5))
                                .name(RandomUtil.randomAlphabeticalString(16))
                                .desc(desc)
                                .access(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC)
                                .signature(null)
                                .phantom(false)
                                .build();

                        final List<Expr> exprs = new ArrayList<>();

                        int index = 0;
                        for (Type arg : parameter.getArgs()) {
                            exprs.add(new VarExpr(methodNode.getCfg().getLocals().get(index), arg));

                            if (arg == Type.DOUBLE_TYPE || arg == Type.LONG_TYPE) {
                                index += 2;
                            } else {
                                index++;
                            }
                        }

                        final Expr[] args = new Expr[exprs.size()];
                        //args[0] = new AllocObjectExpr(parameter.getReturnType());
                        for (int i = 0; i < exprs.size(); i++) {
                            args[i] = exprs.get(i);
                        }

                        final InitialisedObjectExpr initialisedObjectExpr = new InitialisedObjectExpr(
                                method.owner.getName(),
                                method.getDesc(),
                                args
                        );

                        //System.out.println("--> from: " + initialisedObjectExpr.getDesc() + " to: " + Arrays.toString(parameter.getArgs().toArray()));

                        final ReturnStmt returnStmt = new ReturnStmt(
                                parameter.getReturnType(),
                                initialisedObjectExpr
                        );
                        methodNode.getCfg().getEntries().iterator().next().add(returnStmt);


                        for (SkidInvocation invoker : skidInvocations) {
                            if (!(invoker.getExpr() instanceof InitialisedObjectExpr))
                                continue;

                            final List<Expr> parameters = new ArrayList<>(Arrays.asList(invoker.asExpr().getArgumentExprs()));
                            {
                                for (Expr expr : parameters) {
                                    expr.unlink();
                                }

                                //System.out.println("desc: " + methodNode.getDesc());
                                //System.out.println("params: " + Arrays.toString(parameters.toArray()));

                                final Expr[] vars = new Expr[parameters.size()];
                                for (int i = 0; i < parameters.size(); i++) {
                                    vars[i] = parameters.get(i);
                                }

                                final StaticInvocationExpr invocationExpr = new StaticInvocationExpr(
                                        vars,
                                        skidfuscator.getFactoryNode().getName(),
                                        methodNode.getName(),
                                        desc
                                );
                                final Expr expr = invoker.asExpr();
                                expr.getParent().overwrite(expr, invocationExpr);
                                invoker.setExpr(invocationExpr);
                            }
                        }
                    }
        });
    }
}
