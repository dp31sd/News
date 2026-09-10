package testclasses.arrays.unidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class LengthArrayChar {
    private char[] array;

    public LengthArrayChar() {
        this.array = new char[95];
    }

    @NativeObfuscation
    public int getLen() {
        return this.array.length;
    }

}
