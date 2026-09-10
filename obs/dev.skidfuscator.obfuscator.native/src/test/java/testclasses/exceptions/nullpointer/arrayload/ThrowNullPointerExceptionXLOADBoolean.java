package testclasses.exceptions.nullpointer.arrayload;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassBoolean;

public class ThrowNullPointerExceptionXLOADBoolean {
    public ThrowNullPointerExceptionXLOADBoolean() {

    }

    @NativeObfuscation
    public int exec(SupportClassBoolean obj) {
        return obj.b[1] ? 1 : 0;
    }
}
