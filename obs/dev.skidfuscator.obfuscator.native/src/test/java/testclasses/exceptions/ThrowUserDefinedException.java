package testclasses.exceptions;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowUserDefinedException {
    public ThrowUserDefinedException() {

    }

    @NativeObfuscation
    public int exec() throws UserDefinedException {
        throw new UserDefinedException();
    }
}
