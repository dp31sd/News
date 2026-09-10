package testclasses.antidebug.operators;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;
import testclasses.stack.TestingObjectDup;

public class Dup {
    public Dup() {

    }

    @NativeObfuscation
    @AntidebugSelf
    public int exec() {
        TestingObjectDup obj = new TestingObjectDup();
        ++(obj.a);
        return obj.a;
    }
}
