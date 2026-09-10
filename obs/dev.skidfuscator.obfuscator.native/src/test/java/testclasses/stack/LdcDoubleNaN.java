package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcDoubleNaN {
    public LdcDoubleNaN() {

    }

    @NativeObfuscation
    public double exec() {
        return Double.NaN;
    }
}
