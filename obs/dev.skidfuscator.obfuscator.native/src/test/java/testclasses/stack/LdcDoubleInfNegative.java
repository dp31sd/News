package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcDoubleInfNegative {
    public LdcDoubleInfNegative() {

    }

    @NativeObfuscation
    public double exec() {
        return Double.NEGATIVE_INFINITY;
    }
}
