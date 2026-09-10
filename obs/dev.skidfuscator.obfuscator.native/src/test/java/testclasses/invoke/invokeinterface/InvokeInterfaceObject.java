package testclasses.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InvokeInterfaceObject {
    public InvokeInterfaceObject() {

    }

    @NativeObfuscation
    public String concatenate(AdderInterface interf, String a, char b) {
        return interf.concatenate(a, b);
    }
}