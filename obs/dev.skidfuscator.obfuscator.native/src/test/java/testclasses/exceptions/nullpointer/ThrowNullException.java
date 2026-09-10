package testclasses.exceptions.nullpointer;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowNullException {
    public ThrowNullException() {

    }

    @NativeObfuscation
    public int exec() {
        throw null;
    }
}
