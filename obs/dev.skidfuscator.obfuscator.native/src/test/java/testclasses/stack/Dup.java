package testclasses.stack;

import dev.skidfuscator.annotations.NativeObfuscation;

public class Dup {
    public Dup() {

    }

    @NativeObfuscation
    public int exec() {
        TestingObjectDup obj = new TestingObjectDup();
        ++(obj.a);
        return obj.a;
    }
}
