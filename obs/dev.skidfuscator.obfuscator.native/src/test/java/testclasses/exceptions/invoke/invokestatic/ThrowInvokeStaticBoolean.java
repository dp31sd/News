package testclasses.exceptions.invoke.invokestatic;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowInvokeStaticBoolean {
    public ThrowInvokeStaticBoolean() {

    }

    public static boolean normalDiv(int a, int b) {
        return (a / b) > 0;
    }

    @NativeObfuscation
    public int div(int a) {
        return normalDiv(a, 0) ? 0 : -1;
    }
}
