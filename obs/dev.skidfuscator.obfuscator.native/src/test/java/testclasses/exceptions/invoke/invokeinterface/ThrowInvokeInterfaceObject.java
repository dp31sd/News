package testclasses.exceptions.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowInvokeInterfaceObject {
    public ThrowInvokeInterfaceObject() {

    }

    @NativeObfuscation
    public String div(DivisionInterface interf, String a) {
        return interf.div(a);
    }
}
