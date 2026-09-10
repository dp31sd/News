package testclasses.mangling;

import dev.skidfuscator.annotations.NativeObfuscation;

public class UnderscoreMethod {
    public UnderscoreMethod() {

    }

    @NativeObfuscation
    public int _add(int a, int b) {
        return a + b;
    }
}
