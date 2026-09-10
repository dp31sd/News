package testclasses.fields;

import dev.skidfuscator.annotations.NativeObfuscation;

public class GetFieldLong {
    private long fieldJ;

    public GetFieldLong() {
        this.fieldJ = 10000000000L;
    }

    @NativeObfuscation
    public long getField() {
        return this.fieldJ;
    }
}
