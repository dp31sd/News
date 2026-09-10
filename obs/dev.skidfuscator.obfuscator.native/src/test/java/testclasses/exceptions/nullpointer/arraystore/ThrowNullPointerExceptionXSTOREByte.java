package testclasses.exceptions.nullpointer.arraystore;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassByte;

public class ThrowNullPointerExceptionXSTOREByte {
    public ThrowNullPointerExceptionXSTOREByte() {

    }

    @NativeObfuscation
    public int exec(SupportClassByte obj) {
        obj.b[1] = 1;
        return obj.b[1];
    }
}
