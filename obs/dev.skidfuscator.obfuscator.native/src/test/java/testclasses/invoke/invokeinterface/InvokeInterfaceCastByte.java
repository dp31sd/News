package testclasses.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InvokeInterfaceCastByte {
    public InvokeInterfaceCastByte() {

    }

    @NativeObfuscation
    public boolean add(AdderInterface interf, byte a, byte b) {
        return interf.add(a, b) == a + b;
    }
}