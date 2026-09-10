package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class RethrowException {
    public RethrowException() {

    }

    @NativeObfuscation
    public int exec() {
        int res = 0;
        try {
            throw new ClassCastException();
        } catch (ClassCastException e) {
            throw new ClassCastException();
        }
    }
}
