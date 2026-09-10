package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcFloat {
    public LdcFloat() {

    }

    @NativeObfuscation
    public float exec() {
        float a = 3.5f;
        float b = -0.5f;
        return a + b;
    }
}
