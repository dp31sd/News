package testclasses.exceptions.invoke.invokeinterface;

import dev.skidfuscator.annotations.NativeObfuscation;

public class CatchInvokeInterfaceLong {
    public CatchInvokeInterfaceLong() {

    }

    @NativeObfuscation
    public long div(DivisionInterface interf, long a) {
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
