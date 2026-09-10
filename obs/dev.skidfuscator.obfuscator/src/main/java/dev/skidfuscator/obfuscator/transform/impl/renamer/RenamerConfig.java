package dev.skidfuscator.obfuscator.transform.impl.renamer;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.event.annotation.Listen;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

public class RenamerConfig extends DefaultTransformerConfig {
    public RenamerConfig(Config config, String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return this.getBoolean("enabled", false);
    }

    public RenamerType getType() {
        return this.getEnum("type", RenamerType.ALPHABETICAL);
    }

    public List<String> getChars() {
        return this.getStringList("chars", Arrays.asList("l","I","i","1"));
    }

    public int getDepth() {
        return this.getInt("depth", 1);
    }
}
