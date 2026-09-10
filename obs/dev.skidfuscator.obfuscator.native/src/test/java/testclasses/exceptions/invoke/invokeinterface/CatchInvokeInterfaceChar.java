package testclasses.exceptions.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class CatchInvokeInterfaceChar {
    public CatchInvokeInterfaceChar() {

    }

    @NativeObfuscation
    public int div(DivisionInterface interf, char a) {
        int res = 0;
        try {
            res += interf.div(a);
            res++;
        } catch (Exception e) {
            res += 1000;
        }
        return res;
    }
}
