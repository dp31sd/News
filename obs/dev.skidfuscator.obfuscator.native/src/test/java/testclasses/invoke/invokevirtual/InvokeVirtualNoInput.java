package testclasses.invoke.invokevirtual;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InvokeVirtualNoInput {
    public InvokeVirtualNoInput() {

    }

    @NativeObfuscation
    public boolean add(int a, int b) {
        return alwaysTrue();
    }

    public boolean alwaysTrue() {
        return true;
    }
}