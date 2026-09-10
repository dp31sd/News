package dev.skidfuscator.obfuscator.transform.impl.renamer;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.RunClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.skid.FinalSkidTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CustomUserDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CyclicAlphabetDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.resource.ResourceCheck;
import dev.skidfuscator.obfuscator.transform.impl.renamer.resource.impl.BukkitResourceFix;
import dev.skidfuscator.obfuscator.transform.impl.renamer.resource.impl.ManifestResourceFix;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import org.mapleir.asm.FieldNode;
import org.mapleir.asm.MethodNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ClassRenamerTransformer extends AbstractTransformer {
    public ClassRenamerTransformer(Skidfuscator skidfuscator) {
        super(skidfuscator, "Class Renamer");

        switch (this.getConfig().getType()) {
            case CUSTOM: {
                DICTIONARY = new CustomUserDictionary(
                        this.getConfig().getDepth(),
                        this.getConfig().getChars().toArray(new String[0]),
                        true
                );
                break;
            }

            case ALPHABETICAL:
                DICTIONARY = new CyclicAlphabetDictionary();
                break;
            default:
                throw new IllegalStateException("Dictionary of type not found");
        }
    }

    public static RenamerDictionary DICTIONARY;
    private final ResourceCheck[] resourceChecks = new ResourceCheck[] {
            new ManifestResourceFix(),
            new BukkitResourceFix()
    };

    @Listen
    void handle(final RunClassTransformEvent event) {
        final SkidClassNode classNode = event.getClassNode();

        if (classNode.isMixin())
            return;

        final String name = DICTIONARY.next();
        skidfuscator.getClassRemapper().add(
                Type.getObjectType(classNode.getName()).getInternalName(),
                Type.getObjectType(getConfig().getPrefix() + name).getInternalName()
        );

        if (this.getConfig().getType() == RenamerType.CUSTOM) {
            classNode.node.access |= Opcodes.ACC_PUBLIC;

            for (MethodNode method : classNode.getMethods()) {
                final boolean synthetic = method.isSynthetic();
                final boolean natived = method.isNative();
                final boolean abstracted = method.isAbstract();
                final boolean staticed = method.isStatic();
                final boolean finaled = method.isFinal();

                method.node.access = Opcodes.ACC_PUBLIC;
                method.node.access |= synthetic ? Opcodes.ACC_SYNTHETIC : 0;
                method.node.access |= natived ? Opcodes.ACC_NATIVE : 0;
                method.node.access |= abstracted ? Opcodes.ACC_ABSTRACT : 0;
                method.node.access |= staticed ? Opcodes.ACC_STATIC : 0;
                method.node.access |= finaled ? Opcodes.ACC_FINAL : 0;
            }

            for (FieldNode field : classNode.getFields()) {
                final boolean synthetic = field.isSynthetic();
                final boolean volatiled = field.isVolatile();
                final boolean transiented = field.isTransient();
                final boolean staticed = field.isStatic();
                final boolean finaled = field.isFinal();

                field.node.access = Opcodes.ACC_PUBLIC;
                field.node.access |= synthetic ? Opcodes.ACC_SYNTHETIC : 0;
                field.node.access |= volatiled ? Opcodes.ACC_VOLATILE : 0;
                field.node.access |= transiented ? Opcodes.ACC_TRANSIENT : 0;
                field.node.access |= staticed ? Opcodes.ACC_STATIC : 0;
                field.node.access |= finaled ? Opcodes.ACC_FINAL : 0;
            }
        }

    }

    @Listen
    void handle(final FinalSkidTransformEvent event) {
        for (ResourceCheck resourceCheck : resourceChecks) {
            resourceCheck.fix(event.getSkidfuscator());
        }
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new ClassRenamerConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public ClassRenamerConfig getConfig() {
        return (ClassRenamerConfig) super.getConfig();
    }
}
