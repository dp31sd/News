package dev.skidfuscator.backend.obfuscator;

import dev.skidfuscator.backend.session.ObfuscatorUserSession;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;

public class ObfuscatorSession {
    private ObfuscatorUserSession user;
    private SkidfuscatorSession session;

    public ObfuscatorSession(ObfuscatorUserSession user, SkidfuscatorSession session) {
        this.user = user;
        this.session = session;
    }

    public ObfuscatorUserSession getUser() {
        return user;
    }

    public void setUser(ObfuscatorUserSession user) {
        this.user = user;
    }

    public SkidfuscatorSession getSession() {
        return session;
    }

    public void setSession(SkidfuscatorSession session) {
        this.session = session;
    }
}
