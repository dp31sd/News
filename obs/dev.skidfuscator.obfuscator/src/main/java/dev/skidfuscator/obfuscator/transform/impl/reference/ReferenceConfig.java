package dev.skidfuscator.obfuscator.transform.impl.reference;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;

public class ReferenceConfig extends DefaultTransformerConfig {
    public ReferenceConfig(Config config, String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return this.getBoolean("enabled", false);
    }
}
