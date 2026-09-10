package testclasses.exceptions.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowInvokeInterfaceChar {
    public ThrowInvokeInterfaceChar() {

    }

    @NativeObfuscation
    public char div(DivisionInterface interf, char a) {
        return interf.div(a);
    }
}
