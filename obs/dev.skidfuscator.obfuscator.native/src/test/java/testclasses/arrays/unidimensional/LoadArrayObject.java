package testclasses.arrays.unidimensional;

import dev.skidfuscator.annotations.NativeObfuscation;

public class LoadArrayObject {
    private String[] array;

    public LoadArrayObject() {
        this.array = new String[]{"hello", "world"};
    }

    @NativeObfuscation
    public String getVal() {
        return this.array[1];
    }

}
