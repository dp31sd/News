package dev.skidfuscator.obfuscator.transform.impl.renamer;

import com.typesafe.config.Config;

public class ClassRenamerConfig extends RenamerConfig {
    public ClassRenamerConfig(Config config, String path) {
        super(config, path);
    }

    public String getPrefix() {
        return this.getString("prefix", "skid/");
    }
}
