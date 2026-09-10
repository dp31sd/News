package testclasses.invoke.invokespecial;

import dev.skidfuscator.annotations.NativeObfuscation;
import org.junit.jupiter.api.Assertions;

public class InvokeSpecialVoid extends InvokeVirtualVoid {
    public InvokeSpecialVoid() {

    }

    @NativeObfuscation
    @Override
    public void add(int a, int b) {
        super.print(a, b);
    }

    //wrong method, I want the one of the superclass to be called -> invokespecial
    public void print(int a, int b) {
        Assertions.fail("This should not be called. InvokeSpecial has been implemented wrongly");
    }
}