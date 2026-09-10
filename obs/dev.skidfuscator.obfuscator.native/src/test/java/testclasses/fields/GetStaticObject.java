package testclasses.fields;

import dev.skidfuscator.annotations.NativeObfuscation;

public class GetStaticObject {
    private static String fieldL;

    public GetStaticObject() {
        GetStaticObject.fieldL = "hello world";
    }

    @NativeObfuscation
    public String getStatic() {
        return GetStaticObject.fieldL;
    }
}
