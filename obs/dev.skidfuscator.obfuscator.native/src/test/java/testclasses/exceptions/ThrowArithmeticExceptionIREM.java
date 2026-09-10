package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowArithmeticExceptionIREM {
    public ThrowArithmeticExceptionIREM() {

    }

    @NativeObfuscation
    public static int mod(int a) {
        return a % 0;
    }
}
