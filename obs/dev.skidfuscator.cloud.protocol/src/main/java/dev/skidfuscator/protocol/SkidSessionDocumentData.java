package dev.skidfuscator.protocol;

import com.google.gson.annotations.SerializedName;

public class SkidSessionDocumentData extends SessionData {
    @SerializedName("data")
    private String data;

    public SkidSessionDocumentData() {
    }

    public SkidSessionDocumentData(byte[] data) {
        this.data = _encode(data);

        assert data != null : "Sent document data is null?";
    }

    /**
     * @return the jar file to be obfuscated with
     */
    public byte[] getDocument() {
        return _decode(data);
    }

    @Deprecated
    public String getData() {
        return data;
    }
}
