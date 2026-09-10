package dev.skidfuscator.client.artemis.logs;

import dev.skidfuscator.protocol.modal.Severity;

public class Log {
    private final Severity severity;
    private final String message;

    public Log(Severity severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }
}
