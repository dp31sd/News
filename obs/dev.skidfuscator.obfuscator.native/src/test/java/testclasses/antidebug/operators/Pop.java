package testclasses.antidebug.operators;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;

public class Pop {
    private boolean val;

    public Pop() {
        this.val = false;
    }

    @NativeObfuscation
    @AntidebugSelf
    public boolean exec() {
        this.setVal();
        return this.val;
    }

    private float setVal() {
        this.val = true;
        return 1.f;
    }
}
