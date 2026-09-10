package dev.skidfuscator.obfuscator.polymorphic.transforms.model;

public abstract class Rotation extends Transformation {
	protected final long value;
	
	public Rotation(long value, int maxBits) {
		super(maxBits);
		this.value = value;
	}
	
	public long getValue() {
		return value;
	}
	
	/* Both rotations use the same left/right hand sides */
	
	public long lhs() {
		return ((long) maxBits()) - getValue();
	}

	public int lhsInt() {
		return Integer.decode("0x"+Long.toHexString(lhs()));
	}
	
	public long rhs() {
		return getValue();
	}

	public int rhsInt() {
		return Integer.decode("0x"+Long.toHexString(rhs()));
	}
}
