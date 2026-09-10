package testclasses.casts;

import dev.skidfuscator.annotations.NativeObfuscation;

public class CheckCastJavaType {
    public CheckCastJavaType() {

    }

    @NativeObfuscation
    public static String castString(Object a) {
        return (String) a;
    }
}
