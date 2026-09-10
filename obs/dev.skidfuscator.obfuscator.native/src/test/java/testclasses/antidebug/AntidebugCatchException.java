package testclasses.antidebug;

import dev.skidfuscator.j2c.annotations.AntidebugSelf;
import dev.skidfuscator.annotations.NativeObfuscation;

public class AntidebugCatchException {
    public AntidebugCatchException() {

    }

    @NativeObfuscation
    @AntidebugSelf
    public static int convert(String number) {
        try
        {
            return Integer.decode(number);
        } catch (NumberFormatException e)
        {
            //ignore
        }
        return 0;
    }
}
