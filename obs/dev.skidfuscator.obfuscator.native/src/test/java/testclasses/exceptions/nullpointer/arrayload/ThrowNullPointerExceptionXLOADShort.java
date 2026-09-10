package testclasses.exceptions.nullpointer.arrayload;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassShort;

public class ThrowNullPointerExceptionXLOADShort {
    public ThrowNullPointerExceptionXLOADShort() {

    }

    @NativeObfuscation
    public int exec(SupportClassShort obj) {
        return obj.b[1];
    }
}
