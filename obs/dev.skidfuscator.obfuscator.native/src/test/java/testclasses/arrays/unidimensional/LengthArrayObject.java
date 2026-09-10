package testclasses.arrays.unidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class LengthArrayObject {
    private String[] array;

    public LengthArrayObject() {
        this.array = new String[3];
    }

    @NativeObfuscation
    public int getLen() {
        return this.array.length;
    }

}
