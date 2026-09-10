package testclasses.casts; //when changing this, remember to change also the variable in eu.fbk.hardening.helpers

import dev.skidfuscator.annotations.NativeObfuscation;

public class CastFloat2Double {
    public CastFloat2Double() {

    }

    @NativeObfuscation
    public double exec(float a) {
        return a + a;
    }

}
