package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Modulus;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class MulMod extends Modulus {
	public MulMod(long value, long modulo, int maxBits) {
		super(value, modulo, maxBits);
	}

	@Override
	public long transform(long i) {
		if (i != (i*getValue()) / getValue() || i * getValue() >= max())
			throw new ArithmeticException("Multiplicative overflow");
		return (i * getValue()) % getModulo();
	}

	@Override
	public Expr toExpr(PredicateFlowGetter value, SkidBlock block) {
		return new ArithmeticExpr(
				new ConstantExpr(getModuloInt(), Type.INT_TYPE),
				new ArithmeticExpr(
						new ConstantExpr(getValueInt(), Type.INT_TYPE),
						value.get(block),
						ArithmeticExpr.Operator.MUL
				),
				ArithmeticExpr.Operator.REM
		);
	}

	@Override
	public Transformation reversed() {
		return new MulModInv(getValue(), getModulo(), maxBits());
	}
}
