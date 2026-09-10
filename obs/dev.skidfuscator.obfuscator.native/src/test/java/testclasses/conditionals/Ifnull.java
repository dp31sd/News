package testclasses.conditionals;

import dev.skidfuscator.annotations.NativeObfuscation;

public class Ifnull {
    public Ifnull() {

    }

    @NativeObfuscation
    public boolean exec(Object value) {
        return value != null;
    }

}
