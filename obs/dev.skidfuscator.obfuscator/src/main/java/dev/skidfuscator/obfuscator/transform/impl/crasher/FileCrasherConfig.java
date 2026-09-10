package dev.skidfuscator.obfuscator.transform.impl.crasher;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;

public class FileCrasherConfig extends DefaultTransformerConfig {
    public FileCrasherConfig(Config config, String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return this.getBoolean("enabled", false);
    }
}
