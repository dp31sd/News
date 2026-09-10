package testclasses.arrays.unidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class LengthArrayDouble {
    private double[] array;

    public LengthArrayDouble() {
        this.array = new double[79];
    }

    @NativeObfuscation
    public int getLen() {
        return this.array.length;
    }

}
