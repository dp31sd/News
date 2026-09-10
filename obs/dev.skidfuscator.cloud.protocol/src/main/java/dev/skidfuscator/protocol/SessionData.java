package dev.skidfuscator.protocol;

import java.util.Base64;

public abstract class SessionData {
    protected byte[] _decode(final String s) {
        return Base64.getDecoder().decode(s);
    }
    protected String _encode(final byte[] s) {
        return Base64.getEncoder().encodeToString(s);
    }
}
