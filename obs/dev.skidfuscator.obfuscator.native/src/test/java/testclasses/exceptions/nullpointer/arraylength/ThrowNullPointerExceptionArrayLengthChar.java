package testclasses.exceptions.nullpointer.arraylength;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassChar;

public class ThrowNullPointerExceptionArrayLengthChar {
    public ThrowNullPointerExceptionArrayLengthChar() {

    }

    @NativeObfuscation
    public int exec(SupportClassChar obj) {
        return obj.b.length;
    }
}
