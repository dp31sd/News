package dev.skidfuscator.protocol.request;

import com.google.gson.annotations.SerializedName;
import dev.skidfuscator.protocol.SkidSessionDocumentData;

public class DataRequest {
    @SerializedName("data")
    private SkidSessionDocumentData data;

    @SerializedName("execute")
    private boolean execute;

    public DataRequest() {
    }

    public DataRequest(SkidSessionDocumentData data) {
        this.data = data;
    }

    public DataRequest(boolean execute) {
        this.execute = execute;
        this.data = new SkidSessionDocumentData(new byte[0]);
    }

    public SkidSessionDocumentData getData() {
        return data;
    }

    public void setData(SkidSessionDocumentData data) {
        this.data = data;
    }

    public boolean isExecute() {
        return execute;
    }

    public void setExecute(boolean execute) {
        this.execute = execute;
    }
}
