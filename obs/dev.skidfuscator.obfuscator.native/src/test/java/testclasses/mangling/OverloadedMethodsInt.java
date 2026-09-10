package testclasses.mangling;

import dev.skidfuscator.annotations.NativeObfuscation;

public class OverloadedMethodsInt {
    public OverloadedMethodsInt() {

    }

    @NativeObfuscation
    public static int add(int a, double b) {
        return a + (int) b;
    }

    @NativeObfuscation
    public static int add(int a, int b) {
        return a + b;
    }
}
