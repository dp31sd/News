package testclasses.invoke.invokespecial;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InvokeSpecialLong extends InvokeVirtualLong {
    public InvokeSpecialLong() {

    }

    @NativeObfuscation
    @Override
    public long add(long a, long b) {
        return super.normalAdd(a, b);
    }

    //wrong method, I want the one of the superclass to be called -> invokespecial
    public long normalAdd(long a, long b) {
        return a - b;
    }
}