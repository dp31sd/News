package dev.skidfuscator.backend.session;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public class ObfuscatorUserSession implements Principal {
    private final UUID uuid;

    public ObfuscatorUserSession() {
        this.uuid = UUID.randomUUID();
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObfuscatorUserSession)) return false;
        ObfuscatorUserSession that = (ObfuscatorUserSession) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String getName() {
        return uuid.toString();
    }

    @Override
    public boolean implies(Subject subject) {
        return Principal.super.implies(subject);
    }
}
