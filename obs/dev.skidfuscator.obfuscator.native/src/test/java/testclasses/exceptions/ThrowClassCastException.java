package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowClassCastException {
    public ThrowClassCastException() {

    }

    @NativeObfuscation
    public static String castString(Object a) {
        return (String) a;
    }
}
