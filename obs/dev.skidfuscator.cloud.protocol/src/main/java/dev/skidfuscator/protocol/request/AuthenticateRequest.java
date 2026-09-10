package dev.skidfuscator.protocol.request;

import com.google.gson.annotations.SerializedName;
import dev.skidfuscator.protocol.SkidSessionData;

public class AuthenticateRequest {
    @SerializedName("code")
    private String code;

    @SerializedName("session")
    private SkidSessionData session;

    public AuthenticateRequest() {
    }

    public AuthenticateRequest(String code, SkidSessionData session) {
        this.code = code;
        this.session = session;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public SkidSessionData getSession() {
        return session;
    }

    public void setSession(SkidSessionData session) {
        this.session = session;
    }
}