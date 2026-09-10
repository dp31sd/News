package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

public class LdcClassReference {
    public LdcClassReference() {

    }

    @NativeObfuscation
    public Class exec() {
        return String.class;
    }
}
