package testclasses.casts;

import dev.skidfuscator.annotations.NativeObfuscation;

public class InstanceOf {
    public InstanceOf() {

    }

    @NativeObfuscation
    public static boolean instanceofString(Object a) {
        return a instanceof String;
    }
}
