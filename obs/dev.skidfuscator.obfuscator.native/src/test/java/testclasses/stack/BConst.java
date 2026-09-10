package testclasses.stack;


import dev.skidfuscator.annotations.NativeObfuscation;

//BIPUSH
public class BConst {
    public BConst() {

    }

    @NativeObfuscation
    public int exec() {
        byte a = -30;
        byte b = 120;
        return a + b;
    }
}
