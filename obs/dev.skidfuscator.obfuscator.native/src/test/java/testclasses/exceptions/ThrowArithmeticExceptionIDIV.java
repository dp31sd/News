package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowArithmeticExceptionIDIV {
    public ThrowArithmeticExceptionIDIV() {

    }

    @NativeObfuscation
    public static int divide(int a) {
        return a / 0;
    }
}
