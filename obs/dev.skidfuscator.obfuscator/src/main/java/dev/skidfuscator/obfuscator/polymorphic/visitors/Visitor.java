package dev.skidfuscator.obfuscator.polymorphic.visitors;

import dev.skidfuscator.obfuscator.polymorphic.model.Context;
import dev.skidfuscator.obfuscator.polymorphic.model.TransformationChain;
import dev.skidfuscator.obfuscator.polymorphic.transforms.*;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.*;

public interface Visitor<T> {
	T initialise(Context ctx);
	void finalise(T in);
	
	default T visit(Context ctx) {
		T t = initialise(ctx);
		visit(ctx.getReverse(), t);
		finalise(t);
		return t;
	}
	
	default void visit(TransformationChain chain, T in) {
		for (Transformation element : chain)
			visit(element, in);
	}
	
	/* Visit methods */
	
	void visit(Add a, T in);
	void visit(MulMod mm, T in);
	void visit(MulModInv mmi, T in);
	void visit(Not n, T in);
	void visit(Permutation p, T in);
	void visit(RotateLeft rl, T in);
	void visit(RotateRight rr, T in);
	void visit(Substract s, T in);
	void visit(Xor x, T in);
	
	/* Double dispatch visitor methods */
	
	default void visit(Transformation t, T in) {
		if (Add.class.isInstance(t)) {
			visit(Add.class.cast(t), in);
			return;
		} if (MulMod.class.isInstance(t)) {
			visit(MulMod.class.cast(t), in);
			return;
		} if (MulModInv.class.isInstance(t)) {
			visit(MulModInv.class.cast(t), in);
			return;
		} if (Not.class.isInstance(t)) {
			visit(Not.class.cast(t), in);
			return;
		} if (Permutation.class.isInstance(t)) {
			visit(Permutation.class.cast(t), in);
			return;
		} if (RotateLeft.class.isInstance(t)) {
			visit(RotateLeft.class.cast(t), in);
			return;
		} if (RotateRight.class.isInstance(t)) {
			visit(RotateRight.class.cast(t), in);
			return;
		} if (Substract.class.isInstance(t)) {
			visit(Substract.class.cast(t), in);
			return;
		} if (Xor.class.isInstance(t)) {
			visit(Xor.class.cast(t), in);
			return;
		} else
			throw new IllegalStateException("Unimplemented transformation double dispatch");
	}
}
