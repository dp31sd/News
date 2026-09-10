package testclasses.conditionals;

import dev.skidfuscator.annotations.NativeObfuscation;

public class Ificmpeq {
    public Ificmpeq() {

    }

    @NativeObfuscation
    public boolean exec(int value) {
        return value != 1000000000;
    }

}
