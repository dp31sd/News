package testclasses.antidebug;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;

public class AntidebugSelfDebuggingMulticall {
    public AntidebugSelfDebuggingMulticall() {

    }


    public static int doubleAdd(int a, int b) {
        return nativeAdd(a, b) + nativeAdd(a, b);
    }

    @AntidebugSelf
    @NativeObfuscation
    public static int nativeAdd(int a, int b) {
        return a + b;
    }
}
