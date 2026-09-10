package testclasses.exceptions.nullpointer.arrayload;

import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.exceptions.nullpointer.SupportClassFloat;

public class ThrowNullPointerExceptionXLOADFloat {
    public ThrowNullPointerExceptionXLOADFloat() {

    }

    @NativeObfuscation
    public int exec(SupportClassFloat obj) {
        return (int) obj.b[1];
    }
}
