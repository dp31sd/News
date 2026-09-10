package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class Not extends Transformation {
	public Not(int maxBits) {
		super(maxBits);
	}
	
	@Override
	public long transform(long i) {
		return ~i & ((1L << maxBits()) - 1);
	}

	@Override
	public Expr toExpr(PredicateFlowGetter value, SkidBlock block) {
		return new ArithmeticExpr(
				// ((1L << maxBits()) - 1)
				new ArithmeticExpr(
						new ConstantExpr(1, Type.INT_TYPE),
						new ArithmeticExpr(
								new ConstantExpr((int) maxBits(), Type.INT_TYPE),
								new ConstantExpr(1, Type.INT_TYPE),
								ArithmeticExpr.Operator.SHL
						),
						ArithmeticExpr.Operator.SUB
				),
				// ~i
				new ArithmeticExpr(
						new ConstantExpr(-1, Type.INT_TYPE),
						value.get(block),
						ArithmeticExpr.Operator.XOR
				),
				ArithmeticExpr.Operator.AND
		);
	}

	@Override
	public Transformation reversed() {
		return this;
	}
}
