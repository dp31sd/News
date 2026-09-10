package testclasses.newobj;

import dev.skidfuscator.annotations.NativeObfuscation;

public class NewObjectEmpty {
    public NewObjectEmpty() {

    }

    @NativeObfuscation
    public String getObject() {
        return "";
    }
}
