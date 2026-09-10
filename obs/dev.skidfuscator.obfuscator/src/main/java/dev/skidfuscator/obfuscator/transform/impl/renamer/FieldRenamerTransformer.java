package dev.skidfuscator.obfuscator.transform.impl.renamer;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.RunClassTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CustomUserDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CyclicAlphabetDictionary;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class FieldRenamerTransformer extends AbstractTransformer {
    public FieldRenamerTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Field Renamer");

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

    private RenamerDictionary dictionary;

    @Listen
    void handle(final RunClassTransformEvent event) {
        final SkidClassNode classNode = event.getClassNode();

        if (classNode.isMixin())
            return;

        for (FieldNode field : classNode.node.fields) {
            final String name = dictionary.next();
            skidfuscator.getClassRemapper().add(
                    Type.getObjectType(classNode.getName()).getInternalName() + "." + field.name,
                    name
            );
        }

        dictionary.reset();
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
