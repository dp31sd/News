package testclasses.exceptions.nullpointer.arraylength;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassInt;

public class ThrowNullPointerExceptionArrayLengthInt {
    public ThrowNullPointerExceptionArrayLengthInt() {

    }

    @NativeObfuscation
    public int exec(SupportClassInt obj) {
        return obj.b.length;
    }
}
