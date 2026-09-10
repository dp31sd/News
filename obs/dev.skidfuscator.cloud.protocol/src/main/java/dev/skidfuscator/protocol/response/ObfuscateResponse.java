package dev.skidfuscator.protocol.response;

import com.google.gson.annotations.SerializedName;

import java.util.Base64;

public class ObfuscateResponse {
    @SerializedName("data")
    private String data;

    public ObfuscateResponse() {
    }

    public ObfuscateResponse(byte[] data) {
        this.setData(data);
    }

    public byte[] getData() {
        return Base64.getDecoder().decode(data);
    }

    public void setData(byte[] data) {
        this.data = Base64.getEncoder().encodeToString(data);
    }
}
