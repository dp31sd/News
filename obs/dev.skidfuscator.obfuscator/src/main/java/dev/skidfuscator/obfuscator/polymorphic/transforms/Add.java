package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class Add extends Transformation {
	private final long value;
	
	public Add(long value, int maxBits) {
		super(maxBits);
		this.value = value;
	}

	@Override
	public long transform(long i) {
		if (i > max() - value)
			throw new ArithmeticException("Additive overflow");
		return i+value;
	}

	@Override
	public Expr toExpr(final PredicateFlowGetter getter, final SkidBlock block) {
		return new ArithmeticExpr(
				// value
				new ConstantExpr((int) this.value, Type.INT_TYPE),
				// i
				getter.get(block),
				// +
				ArithmeticExpr.Operator.ADD
		);
	}

	@Override
	public Transformation reversed() {
		return new Substract(value, maxBits());
	}
	
	public long getValue() {
		return value;
	}
}
