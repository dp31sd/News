package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowExceptionInNew {
    public ThrowExceptionInNew() {

    }

    @NativeObfuscation
    public int exec() {
        new ClassExceptionInInit();
        return 0;
    }
}
