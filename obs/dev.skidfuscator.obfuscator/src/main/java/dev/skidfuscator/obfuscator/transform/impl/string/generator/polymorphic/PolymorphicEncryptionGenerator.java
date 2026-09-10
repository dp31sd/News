package dev.skidfuscator.obfuscator.transform.impl.string.generator.polymorphic;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.EventBus;
import dev.skidfuscator.obfuscator.event.impl.transform.method.InitMethodTransformEvent;
import dev.skidfuscator.obfuscator.polymorphic.model.Context;
import dev.skidfuscator.obfuscator.polymorphic.model.PolymorphicEngine;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.predicate.renderer.IntegerBlockPredicateRenderer;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.skidasm.SkidMethodNode;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlockFactory;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidControlFlowGraph;
import dev.skidfuscator.obfuscator.skidasm.fake.FakeConditionalJumpStmt;
import dev.skidfuscator.obfuscator.skidasm.fake.FakeUnconditionalJumpStmt;
import dev.skidfuscator.obfuscator.transform.impl.string.generator.EncryptionGeneratorV2;
import dev.skidfuscator.obfuscator.util.TypeUtil;
import org.mapleir.flowgraph.edges.ConditionalJumpEdge;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.flowgraph.edges.UnconditionalJumpEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.mapleir.ir.code.expr.invoke.VirtualInvocationExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.PopStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.utils.CFGUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class PolymorphicEncryptionGenerator implements EncryptionGeneratorV2 {
    private final PolymorphicEngine engine = new PolymorphicEngine(4, 10, 16);
    private final Skidfuscator skidfuscator;

    public PolymorphicEncryptionGenerator(Skidfuscator skidfuscator) {
        this.skidfuscator = skidfuscator;
    }

    @Override
    public Expr encrypt(ConstantExpr input, SkidMethodNode node, BasicBlock block) {
        final SkidControlFlowGraph cfg = node.getCfg();

        final String inputStr = (String) input.getConstant();

        if (inputStr.isEmpty())
            return new ConstantExpr(inputStr, TypeUtil.STRING_TYPE);

        final int index = block.indexOf(input.getRootParent());

        final LocalsPool pool = cfg.getLocals();
        final Local builderLocal = pool.get(pool.getMaxLocals() + 3);
        final Local indexLocal = pool.get(pool.getMaxLocals() + 1);
        final Local charLocal = pool.get(pool.getMaxLocals() + 1);

        final Type type = Type.getType(StringBuilder.class);

        final Context context = engine.transform(inputStr);
        final StringBuilder obfedString = new StringBuilder();
        for (long b : context.getBytes()) {
            obfedString.append((char) b);
        }

        //System.out.println(" Exported: " + obfedString.toString());

        final InitialisedObjectExpr stringBuilder = new InitialisedObjectExpr(
                "java/lang/StringBuilder",
                "(Ljava/lang/String;)V",
                new Expr[]{
                        new ConstantExpr(obfedString.toString(), TypeUtil.STRING_TYPE)
                }
        );

        //System.out.println(" Exported: " + ((ConstantExpr) stringBuilder.getArgumentExprs()[0]).getConstant());
        //System.out.println("index: " + index + " quote:" + block.get(index));
        //System.out.println("(dup) index: " + index + " quote:" + block.get(index));

        final BasicBlock split = CFGUtils.splitBlockReverseFactory(
                SkidBlockFactory.v(skidfuscator),
                cfg,
                block,
                index
        );

        final SkidBlock iterateBlock = new SkidBlock(cfg);
        cfg.addVertex(iterateBlock);
        iterateBlock.setFlag(SkidBlock.FLAG_NO_EXCEPTION, true);

        node.getFlowPredicate().set((SkidBlock) split, node.getBlockPredicate((SkidBlock) block));
        node.getFlowPredicate().set(iterateBlock, node.getBlockPredicate((SkidBlock) block));

        // Create a block such as
        //
        // ┌─────────┐                ┌─────────┐
        // │ Block A │                │ Block A │
        // │ ------- │       -->      └────┬────┘
        // │ "Value" │                     │
        // └─────────┘                ┌────▼────┐
        //                            │ Loop  A │
        //                            └────┬────┘
        //                                 │
        //                            ┌────▼────┐
        //                            │ Block A'│
        //                            └─────────┘
        //
        block.add(new CopyVarStmt(new VarExpr(builderLocal, type), stringBuilder));
        block.add(new CopyVarStmt(new VarExpr(indexLocal, Type.INT_TYPE), new ConstantExpr(0, Type.INT_TYPE)));
        block.add(new CopyVarStmt(new VarExpr(charLocal, Type.INT_TYPE), new ConstantExpr(0, Type.INT_TYPE)));
        //block.add(new FakeUnconditionalJumpStmt(iterateBlock, skidJump));
        cfg.removeEdge(cfg.getImmediateEdge(block));
        cfg.addEdge(new ImmediateEdge<>(
                block,
                iterateBlock
        ));
        //System.out.println(CFGUtils.printBlock(split));

        final ConditionalJumpEdge<BasicBlock> loopBreak = new ConditionalJumpEdge<>(
                iterateBlock,
                split,
                Opcodes.IF_ICMPGE
        );
        cfg.addEdge(loopBreak);
        iterateBlock.add(new FakeConditionalJumpStmt(
                new VarExpr(indexLocal, Type.INT_TYPE),
                new VirtualInvocationExpr(
                        InvocationExpr.CallType.VIRTUAL,
                        new Expr[]{
                                new VarExpr(builderLocal, type)
                        },
                        "java/lang/StringBuilder",
                        "length",
                        "()I"
                ),
                split,
                ConditionalJumpStmt.ComparisonType.GE,
                loopBreak
        ));

        // Add the initial char call
        //
        //    oY_FOvLH = string.charAt(LuLHaKFh);
        //
        iterateBlock.add(new CopyVarStmt(
                    new VarExpr(charLocal, Type.INT_TYPE),
                    new VirtualInvocationExpr(
                            InvocationExpr.CallType.VIRTUAL,
                            new Expr[]{
                                    new VarExpr(builderLocal, type),
                                    new VarExpr(indexLocal, Type.INT_TYPE)
                            },
                            "java/lang/StringBuilder",
                            "charAt",
                            "(I)C"
                    )
                )
        );

        // Add all the permutations
        //
        //    oY_FOvLH = (((oY_FOvLH & 0xffff) << 0x6) | (oY_FOvLH >> 0xa)) & 0xffff;
        //    eebCvtC = ((oY_FOvLH >> 0x0) ^ (oY_FOvLH >> 0x7)) & ((1 << 0x4) - 1);
        //    oY_FOvLH ^= (eebCvtC << 0x0) | (eebCvtC << 0x7);
        //    oY_FOvLH ^= 0x600f;
        //    oY_FOvLH -= 0x2aa2;
        //    oY_FOvLH += 0x25b0;
        //    oY_FOvLH ^= 0x9852;
        //
        final int size = context.getForward().size();
        for (int i = 0; i < size; i++) {
            final Transformation forward = context.getForward().get(i);

            if (IntegerBlockPredicateRenderer.DEBUG) {
                final Local debugLocal = cfg.getLocals().get(cfg.getLocals().getMaxLocals() + 2);
                iterateBlock.add(new CopyVarStmt(
                                new VarExpr(debugLocal, TypeUtil.STRING_TYPE),
                                new ConstantExpr("transformation: "
                                        + forward.getClass().getName()
                                        + "\n"
                                        + " mask: " + forward.getMask()
                                        + " compiled: " + forward.toString()
                                )
                        )
                );
            }
        }
        for (int i = 0; i < size; i++) {
            final Transformation reverse = context.getReverse().get(i);

            if (IntegerBlockPredicateRenderer.DEBUG) {
                final Local debugLocal = cfg.getLocals().get(cfg.getLocals().getMaxLocals() + 2);
                iterateBlock.add(new CopyVarStmt(
                                new VarExpr(debugLocal, TypeUtil.STRING_TYPE),
                                new ConstantExpr("transformation: "
                                        + reverse.getClass().getName()
                                        + "\n"
                                        + " maxBits: " + reverse.maxBits()
                                        + " compiled: " + reverse.toString()
                                )
                        )
                );
            }
            iterateBlock.add(new CopyVarStmt(
                            new VarExpr(charLocal, Type.INT_TYPE),
                            reverse.toExpr(
                                    new PredicateFlowGetter() {
                                        @Override
                                        public Expr get(BasicBlock vertex) {
                                            return new VarExpr(charLocal, Type.INT_TYPE);
                                        }
                                    },
                                    iterateBlock
                            )
                    )
            );
        }

        // Add the char setter
        //
        // string.setCharAt(LuLHaKFh, (char) oY_FOvLH);
        //
        //
        iterateBlock.add(new PopStmt(
                        new VirtualInvocationExpr(
                                InvocationExpr.CallType.VIRTUAL,
                                new Expr[]{
                                        new VarExpr(builderLocal, type),
                                        new VarExpr(indexLocal, Type.INT_TYPE),
                                        new VarExpr(charLocal, Type.INT_TYPE)
                                },
                                "java/lang/StringBuilder",
                                "setCharAt",
                                "(IC)V"
                        )
                )
        );

        // index++
        iterateBlock.add(new CopyVarStmt(
                new VarExpr(indexLocal, Type.INT_TYPE),
                new ArithmeticExpr(
                        new ConstantExpr(1, Type.INT_TYPE),
                        new VarExpr(indexLocal, Type.INT_TYPE),
                        ArithmeticExpr.Operator.ADD
                )
        ));

        final UnconditionalJumpEdge<BasicBlock> loopEdge2 = new UnconditionalJumpEdge<>(
                iterateBlock,
                iterateBlock
        );
        cfg.addEdge(loopEdge2);
        iterateBlock.add(new FakeUnconditionalJumpStmt(iterateBlock, loopEdge2));

        //System.out.println(CFGUtils.printBlock(block));
        //System.out.println(CFGUtils.printBlock(iterateBlock));
        cfg.recomputeEdges();

        return new VirtualInvocationExpr(
                InvocationExpr.CallType.VIRTUAL,
                new Expr[]{
                        new VarExpr(builderLocal, type)
                },
                "java/lang/StringBuilder",
                "toString",
                "()Ljava/lang/String;"
        );
    }

    @Override
    public String decrypt(String input, int key) {
        throw new IllegalStateException(":))");
    }

    @Override
    public void visit(final SkidClassNode node, String name) {
        // TODO: Fix the retardness and make it so that all
        //       generated methods can be properly genned
        final SkidMethodNode skidMethodNode = node.createMethod()
                .access(ACC_PRIVATE | ACC_STATIC)
                .name(name)
                .desc("(Ljava/lang/String;I)Ljava/lang/String;")
                .phantom(true)
                .build();
        EventBus.call(
                new InitMethodTransformEvent(skidMethodNode.getSkidfuscator(), skidMethodNode)
        );
    }
}
