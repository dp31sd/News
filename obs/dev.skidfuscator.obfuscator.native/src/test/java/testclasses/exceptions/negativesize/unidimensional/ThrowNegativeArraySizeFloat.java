package testclasses.exceptions.negativesize.unidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class ThrowNegativeArraySizeFloat {
    private float[] array;

    public ThrowNegativeArraySizeFloat() {

    }

    @NativeObfuscation
    public int exec() {
        this.array = new float[-1];
        return 0;
    }
}
