package testclasses.casts; //when changing this, remember to change also the variable in eu.fbk.hardening.helpers

import dev.skidfuscator.annotations.NativeObfuscation;

public class CastDouble2Long {
    public CastDouble2Long() {

    }

    @NativeObfuscation
    public long exec(double a) {
        return (long) (a + a);
    }

}
