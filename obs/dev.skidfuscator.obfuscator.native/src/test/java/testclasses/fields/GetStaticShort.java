package testclasses.fields;

import dev.skidfuscator.annotations.NativeObfuscation;

public class GetStaticShort {
    private static short fieldS;

    public GetStaticShort() {
        GetStaticShort.fieldS = 3000;
    }

    @NativeObfuscation
    public short getStatic() {
        return GetStaticShort.fieldS;
    }
}
