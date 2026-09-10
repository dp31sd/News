package dev.skidfuscator.protocol.response;

import com.google.gson.annotations.SerializedName;

public class TextResponse {
    @SerializedName("text")
    private String text;

    @SerializedName("error")
    private boolean error;

    public TextResponse() {
    }

    public TextResponse(String text, boolean error) {
        this.text = text;
        this.error = error;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public static TextResponse success(final String text) {
        return new TextResponse(text, false);
    }

    public static TextResponse error(final String text) {
        return new TextResponse(text, true);
    }
}
