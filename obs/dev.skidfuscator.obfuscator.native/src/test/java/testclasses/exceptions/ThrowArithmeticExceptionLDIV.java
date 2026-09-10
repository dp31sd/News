package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowArithmeticExceptionLDIV {
    public ThrowArithmeticExceptionLDIV() {

    }

    @NativeObfuscation
    public static long divide(long a) {
        return a / 0L;
    }
}
