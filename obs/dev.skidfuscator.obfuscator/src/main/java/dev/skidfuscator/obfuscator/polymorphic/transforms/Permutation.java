package dev.skidfuscator.obfuscator.polymorphic.transforms;

import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.predicate.factory.PredicateFlowGetter;
import dev.skidfuscator.obfuscator.skidasm.cfg.SkidBlock;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.objectweb.asm.Type;

public class Permutation extends Transformation {
	private final long pos1, pos2, bits;
	
	public Permutation(int pos1, int pos2, int bits, int maxBits) {
		super(maxBits);
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.bits = bits;
		if (Math.max(pos1, pos2) + bits > maxBits)
			throw new ArithmeticException("Invalid ranges");
	}

	@Override
	public long transform(long i) {
		long xor = ((i >> pos1) ^ (i >> pos2)) & ((1L << bits) - 1L);
		return i ^ ((xor << pos1) | (xor << pos2));
	}

	@Override
	public Transformation reversed() {
		return this;
	}

	@Override
	public Expr toExpr(PredicateFlowGetter value, SkidBlock block) {
		PredicateFlowGetter xor = new PredicateFlowGetter() {
			@Override
			public Expr get(BasicBlock vertex) {
				return new ArithmeticExpr(
						new ArithmeticExpr(
								new ConstantExpr(1, Type.INT_TYPE),
								new ArithmeticExpr(
										new ConstantExpr(getBitsInt(), Type.INT_TYPE),
										new ConstantExpr(1, Type.INT_TYPE),
										ArithmeticExpr.Operator.SHL
								),
								ArithmeticExpr.Operator.SUB
						),
						new ArithmeticExpr(
								new ArithmeticExpr(
										new ConstantExpr(getPosition2Int(), Type.INT_TYPE),
										value.get(block),
										ArithmeticExpr.Operator.SHR
								),
								new ArithmeticExpr(
										new ConstantExpr(getPosition1Int(), Type.INT_TYPE),
										value.get(block),
										ArithmeticExpr.Operator.SHR
								),
								ArithmeticExpr.Operator.XOR
						),
						ArithmeticExpr.Operator.AND
				);
			}
		};
		return new ArithmeticExpr(
				new ArithmeticExpr(
					new ArithmeticExpr(
							new ConstantExpr(getPosition2Int(), Type.INT_TYPE),
							xor.get(block),
							ArithmeticExpr.Operator.SHL
					),
					new ArithmeticExpr(
							new ConstantExpr(getPosition1Int(), Type.INT_TYPE),
							xor.get(block),
							ArithmeticExpr.Operator.SHL
					),
					ArithmeticExpr.Operator.OR
				),
				value.get(block),
				ArithmeticExpr.Operator.XOR
		);
	}

	public long getPosition1() {
		return pos1;
	}

	public int getPosition1Int() {
		return Integer.decode("0x"+Long.toHexString(getPosition1()));
	}
	
	public long getPosition2() {
		return pos2;
	}

	public int getPosition2Int() {
		return Integer.decode("0x"+Long.toHexString(getPosition2()));
	}
	
	public long getBits() {
		return bits;
	}

	public int getBitsInt() {
		return Integer.decode("0x"+Long.toHexString(getBits()));
	}
}
