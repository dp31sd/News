package testclasses.exceptions.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowInvokeInterfaceByte {
    public ThrowInvokeInterfaceByte() {

    }

    @NativeObfuscation
    public byte div(DivisionInterface interf, byte a) {
        return interf.div(a);
    }
}
