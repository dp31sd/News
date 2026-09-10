package testclasses.exceptions.nullpointer.setfield;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassByte;

public class ThrowNullPointerExceptionSetFieldByte {
    public ThrowNullPointerExceptionSetFieldByte() {

    }

    @NativeObfuscation
    public byte exec(SupportClassByte obj) {
        obj.a = 0;
        return 0;
    }
}
