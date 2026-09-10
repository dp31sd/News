package testclasses.fields;

import dev.skidfuscator.annotations.NativeObfuscation;

public class GetFieldObject {
    private String fieldL;

    public GetFieldObject() {
        this.fieldL = "hello world";
    }

    @NativeObfuscation
    public String getField() {
        return this.fieldL;
    }
}
