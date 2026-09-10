package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcInt {
    public LdcInt() {

    }

    @NativeObfuscation
    public int exec() {
        int a = 1000000000;
        int b = -1000000001;
        return a + b;
    }
}
