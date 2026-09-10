package testclasses.exceptions.negativesize.multidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowNegativeArraySizeObject {
    private Object[][][] array;

    public ThrowNegativeArraySizeObject() {

    }

    @NativeObfuscation
    public int exec() {
        this.array = new String[-1][1][1];
        return 0;
    }
}
