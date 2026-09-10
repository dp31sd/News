package testclasses.exceptions.negativesize.multidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowNegativeArraySizeDouble {
    private double[][][] array;

    public ThrowNegativeArraySizeDouble() {

    }

    @NativeObfuscation
    public int exec() {
        this.array = new double[-1][1][1];
        return 0;
    }
}
