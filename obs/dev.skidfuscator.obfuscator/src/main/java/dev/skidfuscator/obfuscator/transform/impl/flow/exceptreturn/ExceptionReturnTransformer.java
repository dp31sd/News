package dev.skidfuscator.obfuscator.transform.impl.flow.exceptreturn;

import dev.skidfuscator.builder.FieldNodeBuilder;
import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.EventPriority;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.group.FinalGroupTransformEvent;
import dev.skidfuscator.obfuscator.predicate.renderer.seed.impl.StaticSeedLoaderRenderer;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.skidasm.SkidGroup;
import dev.skidfuscator.obfuscator.skidasm.SkidInvocation;
import dev.skidfuscator.obfuscator.skidasm.SkidMethodNode;
import dev.skidfuscator.obfuscator.skidasm.builder.SkidClassNodeBuilder;
import dev.skidfuscator.obfuscator.skidasm.builder.SkidMethodNodeBuilder;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlockFactory;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidControlFlowGraph;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import org.mapleir.asm.MethodNode;
import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.flowgraph.edges.UnconditionalJumpEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.CaughtExceptionExpr;
import org.mapleir.ir.code.expr.FieldLoadExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.code.expr.invoke.VirtualInvocationExpr;
import org.mapleir.ir.code.stmt.*;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.utils.CFGUtils;
import org.mapleir.ir.utils.Parameter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class ExceptionReturnTransformer extends AbstractTransformer {
    public ExceptionReturnTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Exception Return");
    }

    private final Map<Type, SkidClassNode> exceptions = new HashMap<>();
    @Listen(EventPriority.HIGHEST)
    void handle(final FinalGroupTransformEvent event) {
        final SkidGroup group = event.getGroup();

        if (group.isInit()
                || group.isEntryPoint()
                || group.isSynthetic()
                || group.isStatical() && group.getName().equalsIgnoreCase("main"))
            return;

        if (group.hasAttribute(StaticSeedLoaderRenderer.LOADER))
            return;

        final Parameter groupDesc = new Parameter(group.getDesc());
        final Type returnType = groupDesc.getReturnType();

        final SkidClassNode exception = this.getWrapper(returnType);
        final Type exceptionType = exception.getType();

        for (SkidInvocation invocation : group.getInvokers()) {
            final CodeUnit parent = invocation.asExpr().getParent();

            // TODO: Dynamically update invokers
            if (parent == null)
                continue;

            final SkidBlock parentBlock = (SkidBlock) parent.getBlock();
            final SkidControlFlowGraph parentCfg = (SkidControlFlowGraph) parentBlock.cfg;

            final Expr invoker = invocation.asExpr();
            final Stmt rootParent = invoker.getRootParent();
            invoker.setParent(null);

            /*
             * Create a new block such that
             *
             *  [ old statement which throws exception ]
             *                   |
             *     ______________v______________
             *    [___________Handler___________]
             *    |                             |
             *    |          (.......)          |
             *    |_____________________________|
             */
            final int rootParentIndex = parentBlock.indexOf(rootParent);
            final BasicBlock newBlock = CFGUtils.splitBlockReverseFactory(
                    SkidBlockFactory.v(skidfuscator),
                    parentCfg,
                    parentBlock,
                    rootParentIndex
            );

            final BasicBlock oldBlock = parentBlock.isEmpty() ? parentBlock : CFGUtils.splitBlockReverseFactory(
                    SkidBlockFactory.v(skidfuscator),
                    parentCfg,
                    parentBlock,
                    rootParentIndex
            );

            /* Step 1: Add exception range */
            final ExceptionRange<BasicBlock> range = new ExceptionRange<>();
            final BasicBlock catcher = new SkidBlock(parentCfg);
            parentCfg.addVertex(catcher);

            // Set the type to our custom exception
            range.addType(exceptionType);

            // Add parent block as follower
            range.addVertex(oldBlock);

            // Set handler as the block with the invoke statement
            range.setHandler(catcher);

            // Add range
            parentCfg.addRange(0, range);
            parentCfg.addEdge(new TryCatchEdge<>(oldBlock, range));

            // Add jumps
            final UnconditionalJumpEdge<BasicBlock> jmp = new UnconditionalJumpEdge<>(
                    catcher,
                    newBlock
            );
            catcher.add(new UnconditionalJumpStmt(newBlock, jmp));

            /* Step 2: Add the exception catcher */
            if (returnType.equals(Type.VOID_TYPE)) {
                catcher.add(0, new PopStmt(
                        new CaughtExceptionExpr(exceptionType))
                );
                newBlock.remove(rootParent);
            } else {
                final Local local = parentCfg.getLocals().get(parentCfg.getLocals().getMaxLocals() + 2);
                catcher.add(0, new CopyVarStmt(
                        new VarExpr(local, exceptionType),
                        new CaughtExceptionExpr(exceptionType))
                );

                /* Step 3: Replace expression with self */
                parent.overwrite(
                        invoker,
                        new VirtualInvocationExpr(
                                InvocationExpr.CallType.VIRTUAL,
                                new Expr[] {
                                        new VarExpr(local, exceptionType)
                                },
                                exception.getName(),
                                "get",
                                "()" + returnType.getDescriptor()
                        )
                );
            }

            oldBlock.add(new PopStmt(invoker));

            final BasicBlock bogusTarget = parentBlock;
            final UnconditionalJumpEdge<BasicBlock> loop = new UnconditionalJumpEdge<>(
                    oldBlock,
                    bogusTarget
            );
            parentCfg.addEdge(loop);
            oldBlock.add(new UnconditionalJumpStmt(
                    bogusTarget,
                    loop
            ));
        }

        /*
         * Replace all the return calls in a method with a throw statement
         * instead, then from that point on we'll be able to add more bogus
         * statements
         */
        for (MethodNode methodNode : group.getMethodNodeList()) {
            if (!(methodNode instanceof SkidMethodNode)) {
                continue;
            }

            final SkidMethodNode skidMethodNode = (SkidMethodNode) methodNode;

            for (BasicBlock vertex : new LinkedList<>(skidMethodNode.getCfg().vertices())) {
                for (Stmt stmt : new LinkedList<>(vertex)) {
                    if (!(stmt instanceof ReturnStmt))
                        continue;

                    final Expr returnExpr = ((ReturnStmt) stmt).getExpression();
                    final Stmt replace;

                    if (returnExpr == null) {
                        replace = new ThrowStmt(
                                new InitialisedObjectExpr(
                                        exception.getName(),
                                        "()V",
                                        new Expr[0]
                                )
                        );
                    } else {
                        returnExpr.unlink();
                        returnExpr.setParent(null);
                        replace = new ThrowStmt(
                                new InitialisedObjectExpr(
                                        exception.getName(),
                                        "(" + returnType.getDescriptor() + ")V",
                                        new Expr[] {
                                                returnExpr
                                        }
                                )
                        );
                    }

                    vertex.add(vertex.indexOf(stmt), replace);
                    vertex.remove(stmt);
                }
            }
        }


        /*
         * Finally, change the description of the method permanently
         * + micro optimization for less redundant calls lmao
         */
        if (!returnType.equals(Type.VOID_TYPE)) {
            groupDesc.setReturnArg(Type.VOID_TYPE);
            group.setDesc(groupDesc.getDesc());
        }
    }

    private SkidClassNode getWrapper(final Type returnType) {
        SkidClassNode exception = exceptions.get(returnType);


        if (exception == null) {
            exception = new SkidClassNodeBuilder(skidfuscator)
                    .access(Opcodes.ACC_PUBLIC)
                    .name("skid/" + RandomUtil.randomAlphabeticalString(16))
                    .superName("java/lang/RuntimeException")
                    .phantom(true)
                    .build();

            final SkidMethodNode init = new SkidMethodNodeBuilder(skidfuscator, exception)
                    .access(Opcodes.ACC_PUBLIC)
                    .name("<init>")
                    .desc(returnType.equals(Type.VOID_TYPE) ? "()V" : "(" + returnType.getDescriptor() + ")V")
                    .phantom(true)
                    .build();

            init.getCfg().getEntry().add(new PopStmt(new VirtualInvocationExpr(
                    InvocationExpr.CallType.SPECIAL,
                    new Expr[] {
                            new VarExpr(init.getCfg().getSelfLocal(), exception.getType())
                    },
                    "java/lang/RuntimeException",
                    "<init>",
                    "()V"
            )));

            if (!returnType.equals(Type.VOID_TYPE)) {
                final FieldNode local = new FieldNodeBuilder()
                        .access(Opcodes.ACC_PRIVATE)
                        .desc(returnType.getDescriptor())
                        .name("var")
                        .value(null)
                        .build();

                exception.node.fields.add(local);

                final SkidMethodNode getter = new SkidMethodNodeBuilder(skidfuscator, exception)
                        .access(Opcodes.ACC_PUBLIC)
                        .name("get")
                        .desc("()" + returnType.getDescriptor())
                        .phantom(true)
                        .build();

                getter.getCfg().getEntry().add(new ReturnStmt(
                        returnType,
                        new FieldLoadExpr(
                                new VarExpr(getter.getCfg().getSelfLocal(), exception.getType()),
                                exception.getName(),
                                local.name,
                                local.desc,
                                false
                        )
                ));

                init.getCfg().getEntry().add(new FieldStoreStmt(
                        new VarExpr(getter.getCfg().getSelfLocal(), exception.getType()),
                        new VarExpr(getter.getCfg().getLocals().get(1), returnType),
                        exception.getName(),
                        local.name,
                        local.desc,
                        false
                ));
            }

            init.getCfg().getEntry().add(new ReturnStmt());

            exceptions.put(returnType, exception);
            skidfuscator.getClassSource().add(exception);
            skidfuscator
                    .getJarContents()
                    .getClassContents()
                    .add(
                            new JarClassData(
                                    exception.getName() + ".class",
                                    exception.toByteArray(),
                                    exception
                            )
                    );
        }

        return exception;
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new ExceptionReturnConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public ExceptionReturnConfig getConfig() {
        return (ExceptionReturnConfig) super.getConfig();
    }
}
