package dev.skidfuscator.obfuscator.transform.impl.flow.exceptreturn;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.transform.impl.flow.exception.BasicExceptionStrength;

public class ExceptionReturnConfig extends DefaultTransformerConfig {
    public ExceptionReturnConfig(Config config, String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return this.getBoolean("enabled", false);
    }
}
