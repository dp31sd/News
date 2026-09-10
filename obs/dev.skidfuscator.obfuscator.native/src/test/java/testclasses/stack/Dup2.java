package testclasses.stack;

import dev.skidfuscator.annotations.NativeObfuscation;

public class Dup2 {
    public Dup2() {

    }

    @NativeObfuscation
    public double exec() {
        double a = 2.5;
        double b = 3.5;
        return a = b++;
    }
}
