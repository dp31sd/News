package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Rotation;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class RotateLeft extends Rotation {
	public RotateLeft(long value, int maxBits) {
		super(value, maxBits);
	}

	@Override
	public long transform(long i) {
		return (((i & getMask()) >> lhs()) | (i << rhs())) & getMask();
	}

	@Override
	public Expr toExpr(final PredicateFlowGetter value, final SkidBlock block) {
		// (((i & getMask()) >> lhs()) | (i << rhs())) & getMask()
		return new ArithmeticExpr(
				// getMask()
				new ConstantExpr(getMaskInt(), Type.INT_TYPE),
				// (((i & getMask()) >> lhs()) | (i << rhs()))
				new ArithmeticExpr(
						// (i << rhs())
						new ArithmeticExpr(
								new ConstantExpr(rhsInt(), Type.INT_TYPE),
								value.get(block),
								ArithmeticExpr.Operator.SHL
						),
						// ((i & getMask()) >> lhs())
						new ArithmeticExpr(
								// lhs()
								new ConstantExpr(lhsInt(), Type.INT_TYPE),
								// (i & getMask())
								new ArithmeticExpr(
										new ConstantExpr(getMaskInt(), Type.INT_TYPE),
										value.get(block),
										ArithmeticExpr.Operator.AND
								),
								ArithmeticExpr.Operator.SHR
						),
						ArithmeticExpr.Operator.OR
				),
				ArithmeticExpr.Operator.AND
		);
	}

	@Override
	public Transformation reversed() {
		return new RotateRight(getValue(), maxBits());
	}

	@Override
	public String toString() {
		return "RotateLeft{" +
				"value=" + value +
				'}';
	}
}
