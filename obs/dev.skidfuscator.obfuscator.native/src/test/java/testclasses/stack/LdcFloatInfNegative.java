package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcFloatInfNegative {
    public LdcFloatInfNegative() {

    }

    @NativeObfuscation
    public float exec() {
        return Float.NEGATIVE_INFINITY;
    }
}
