package dev.skidfuscator.obfuscator.transform.impl.j2c.driver;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;

public class NativeDriverConfig extends DefaultTransformerConfig {
    public NativeDriverConfig(Config config, String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return this.getBoolean("enabled", false);
    }

    public String getName() {
        return this.getString("path", "skid/Driver");
    }
}
