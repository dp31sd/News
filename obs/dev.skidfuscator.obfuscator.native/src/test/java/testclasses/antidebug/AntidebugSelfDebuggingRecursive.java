package testclasses.antidebug;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;

public class AntidebugSelfDebuggingRecursive {
    public AntidebugSelfDebuggingRecursive() {

    }

    @NativeObfuscation
    @AntidebugSelf
    public static long factorial(int a) {
        if (a == 0) {
            return 1;
        } else {
            return a * factorial(a - 1);
        }
    }
}
