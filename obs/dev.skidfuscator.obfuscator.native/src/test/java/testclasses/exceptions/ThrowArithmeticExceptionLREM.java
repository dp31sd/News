package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowArithmeticExceptionLREM {
    public ThrowArithmeticExceptionLREM() {

    }

    @NativeObfuscation
    public static long mod(long a) {
        return a % 0L;
    }
}
