package dev.skidfuscator.protocol;

import com.google.gson.annotations.SerializedName;

import java.util.Base64;

/**
 * The Skidfuscator session modal used for DAO interactions.
 * (Data Access Object meaning what's passed in the REST
 * requests)
 */
public class SkidSessionData extends SessionData {
    @SerializedName("renamer")
    private boolean renamer;

    @SerializedName("config")
    private String config;

    /**
     * @return the config file to be obfuscated
     */
    public byte[] getConfig() {
        return _decode(config);
    }

    public void setConfig(byte[] config) {
        this.config = Base64.getEncoder().encodeToString(config);
    }

    public boolean isRenamer() {
        return renamer;
    }

    public void setRenamer(boolean renamer) {
        this.renamer = renamer;
    }
}
