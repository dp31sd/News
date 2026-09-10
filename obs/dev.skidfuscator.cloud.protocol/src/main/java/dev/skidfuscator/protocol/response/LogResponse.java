package dev.skidfuscator.protocol.response;

import com.google.gson.annotations.SerializedName;
import dev.skidfuscator.protocol.modal.Severity;

public class LogResponse {
    @SerializedName("severity")
    private Severity severity;

    @SerializedName("message")
    private String message;

    public LogResponse() {
    }

    public LogResponse(Severity severity, String message) {
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
