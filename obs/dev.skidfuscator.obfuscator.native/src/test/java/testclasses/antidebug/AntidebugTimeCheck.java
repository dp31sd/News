package testclasses.antidebug;

import dev.skidfuscator.j2c.annotations.AntidebugTime;
import dev.skidfuscator.annotations.NativeObfuscation;

public class AntidebugTimeCheck {
    public AntidebugTimeCheck() {

    }

    @NativeObfuscation
    @AntidebugTime
    public static int add(int a, int b) {
        return a + b;
    }
}
