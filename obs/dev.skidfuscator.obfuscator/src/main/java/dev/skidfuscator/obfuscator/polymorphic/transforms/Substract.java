package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class Substract extends Transformation {
	private final long value;
	
	public Substract(long value, int maxBits) {
		super(maxBits);
		this.value = value;
	}

	@Override
	public long transform(long i) {
		if (i < value)
			throw new ArithmeticException("Substraction underflow");
		return i - value;
	}

	@Override
	public Expr toExpr(final PredicateFlowGetter getter, final SkidBlock block) {
		return new ArithmeticExpr(
				new ConstantExpr((int) this.value, Type.INT_TYPE),
				getter.get(block),
				ArithmeticExpr.Operator.SUB
		);
	}

	@Override
	public Transformation reversed() {
		return new Add(value, maxBits());
	}

	public long getValue() {
		return value;
	}
}
