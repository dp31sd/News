package dev.skidfuscator.obfuscator.polymorphic.visitors;

import dev.skidfuscator.obfuscator.polymorphic.model.Context;
import dev.skidfuscator.obfuscator.polymorphic.transforms.*;
import org.objectweb.asm.tree.MethodNode;

public class MethodVisitor extends LanguageVisitor {
    private final MethodNode node;

    public MethodVisitor(MethodNode node) {
        this.node = node;
    }

    @Override
    public StringBuilder initialise(Context ctx) {

        return null;
    }

    @Override
    public void finalise(StringBuilder in) {

    }

    @Override
    public void visit(Add a, StringBuilder in) {

    }

    @Override
    public void visit(MulMod mm, StringBuilder in) {

    }

    @Override
    public void visit(MulModInv mmi, StringBuilder in) {

    }

    @Override
    public void visit(Not n, StringBuilder in) {

    }

    @Override
    public void visit(Permutation p, StringBuilder in) {

    }

    @Override
    public void visit(RotateLeft rl, StringBuilder in) {

    }

    @Override
    public void visit(RotateRight rr, StringBuilder in) {

    }

    @Override
    public void visit(Substract s, StringBuilder in) {

    }

    @Override
    public void visit(Xor x, StringBuilder in) {

    }
}
