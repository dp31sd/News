package testclasses.invoke.invokestatic;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InvokeStaticInt {
    public InvokeStaticInt() {

    }

    public static int normalAdd(int a, int b) {
        return a + b;
    }

    @NativeObfuscation
    public int add(int a, int b) {
        return InvokeStaticInt.normalAdd(a, b);
    }
}