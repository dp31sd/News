package testclasses.arithmetic; //when changing this, remember to change also the variable in eu.fbk.hardening.helpers

import dev.skidfuscator.annotations.NativeObfuscation;

public class INeg {
    public INeg() {

    }

    @NativeObfuscation
    public int exec(int a) {
        return -a;
    }

}
