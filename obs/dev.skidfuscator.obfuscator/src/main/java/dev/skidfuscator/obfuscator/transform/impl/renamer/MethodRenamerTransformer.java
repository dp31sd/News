package dev.skidfuscator.obfuscator.transform.impl.renamer;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.EventPriority;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.group.FinalGroupTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidGroup;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CustomUserDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CyclicAlphabetDictionary;
import dev.skidfuscator.obfuscator.util.MiscUtil;

public class MethodRenamerTransformer extends AbstractTransformer {
    public MethodRenamerTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Method Renamer");

        switch (this.getConfig().getType()) {
            case CUSTOM: {
                dictionary = new CustomUserDictionary(
                        this.getConfig().getDepth(),
                        this.getConfig().getChars().toArray(new String[0]),
                        false
                );
                break;
            }

            case ALPHABETICAL:
                dictionary = new CyclicAlphabetDictionary();
                break;
            default:
                throw new IllegalStateException("Dictionary of type not found");
        }
    }
    private RenamerDictionary dictionary = new CyclicAlphabetDictionary();

    @Listen(EventPriority.MONITOR)
    void handle(final FinalGroupTransformEvent event) {
        final SkidGroup group = event.getGroup();

        if (!group.isApplication()
                || group.isAnnotation()
                || group.getName().contains("<")
                || group.isSynthetic()
                || group.isInit()
                || group.isClinit()
                || group.isNatived()
                || group.isMixin()
        )
            return;

        if (group.getName().equals("main") && group.isStatical())
            return;

        group.setName(dictionary.next());
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new RenamerConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public RenamerConfig getConfig() {
        return (RenamerConfig) super.getConfig();
    }
}
