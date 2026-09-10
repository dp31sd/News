package dev.skidfuscator.obfuscator.transform.impl.crasher;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.util.MiscUtil;

public class FileCrasherTransformer extends AbstractTransformer {
    public FileCrasherTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "File Crasher");
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new FileCrasherConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public FileCrasherConfig getConfig() {
        return (FileCrasherConfig) super.getConfig();
    }
}
