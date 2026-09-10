package testclasses.exceptions.nullpointer.getfield;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassChar;

public class ThrowNullPointerExceptionGetFieldChar {
    public ThrowNullPointerExceptionGetFieldChar() {

    }

    @NativeObfuscation
    public char exec(SupportClassChar obj) {
        return obj.a;
    }
}
