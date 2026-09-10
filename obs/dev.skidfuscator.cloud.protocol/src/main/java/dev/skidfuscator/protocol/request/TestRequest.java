package dev.skidfuscator.protocol.request;

import com.google.gson.annotations.SerializedName;

public class TestRequest {
    @SerializedName("code")
    private String code;

    public TestRequest() {
    }

    public TestRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
