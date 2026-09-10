package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcFloatNaN {
    public LdcFloatNaN() {

    }

    @NativeObfuscation
    public float exec() {
        return Float.NaN;
    }
}
