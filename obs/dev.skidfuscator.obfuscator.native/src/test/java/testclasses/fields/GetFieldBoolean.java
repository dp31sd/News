package testclasses.fields;

import dev.skidfuscator.annotations.NativeObfuscation;

public class GetFieldBoolean {
    private boolean fieldZ;

    public GetFieldBoolean() {
        this.fieldZ = true;
    }

    @NativeObfuscation
    public boolean getField() {
        return this.fieldZ;
    }
}
