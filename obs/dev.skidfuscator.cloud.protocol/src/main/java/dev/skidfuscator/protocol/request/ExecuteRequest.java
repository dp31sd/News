package dev.skidfuscator.protocol.request;

import com.google.gson.annotations.SerializedName;

public class ExecuteRequest {
    @SerializedName("code")
    private String code;

    public ExecuteRequest() {
    }

    public ExecuteRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
