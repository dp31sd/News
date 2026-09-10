package testclasses.exceptions.nullpointer.getfield;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassByte;

public class ThrowNullPointerExceptionGetFieldByte {
    public ThrowNullPointerExceptionGetFieldByte() {

    }

    @NativeObfuscation
    public byte exec(SupportClassByte obj) {
        return obj.a;
    }
}
