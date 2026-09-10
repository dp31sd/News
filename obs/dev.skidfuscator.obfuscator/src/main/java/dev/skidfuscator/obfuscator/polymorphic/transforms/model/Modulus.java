package dev.skidfuscator.obfuscator.polymorphic.transforms.model;

public abstract class Modulus extends Transformation {
	private final long value, modulo;
	
	public Modulus(long value, long modulo, int maxBits) {
		super(maxBits);
		this.value = value;
		this.modulo = modulo;
	}
	
	public long getValue() {
		return value;
	}

	public int getValueInt() {
		return Integer.decode("0x"+Long.toHexString(value));
	}
	
	public long getModulo() {
		return modulo;
	}

	public int getModuloInt() {
		return Integer.decode("0x"+Long.toHexString(modulo));
	}
}
