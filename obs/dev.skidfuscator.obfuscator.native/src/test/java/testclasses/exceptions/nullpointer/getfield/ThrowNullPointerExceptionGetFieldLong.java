package testclasses.exceptions.nullpointer.getfield;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassLong;

public class ThrowNullPointerExceptionGetFieldLong {
    public ThrowNullPointerExceptionGetFieldLong() {

    }

    @NativeObfuscation
    public long exec(SupportClassLong obj) {
        return obj.a;
    }
}
