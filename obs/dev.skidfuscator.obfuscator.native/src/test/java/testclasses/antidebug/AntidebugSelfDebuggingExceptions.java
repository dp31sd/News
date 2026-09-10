package testclasses.antidebug;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;

public class AntidebugSelfDebuggingExceptions {
    public AntidebugSelfDebuggingExceptions() {

    }

    @NativeObfuscation
    @AntidebugSelf
    public static String name(String oname) {
        if (oname == null) {
            throw new IllegalArgumentException("oname cannot be null");
        }

        return "placeholder";
    }
}
