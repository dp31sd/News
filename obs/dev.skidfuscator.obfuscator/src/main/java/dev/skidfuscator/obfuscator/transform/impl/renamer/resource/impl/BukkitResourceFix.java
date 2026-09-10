package dev.skidfuscator.obfuscator.transform.impl.renamer.resource.impl;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.transform.impl.renamer.resource.ResourceCheck;
import org.objectweb.asm.Type;
import org.topdank.byteengineer.commons.data.JarResource;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;

public class BukkitResourceFix implements ResourceCheck {
    @Override
    public void fix(Skidfuscator skidfuscator) {
        final Map<String, JarResource> containerMap = skidfuscator
                .getJarContents()
                .getResourceContents()
                .namedMap();

        if (!containerMap.containsKey("plugin.yml")) {
            return;
        }

        Skidfuscator.LOGGER.post("Activating Bukkit renaming for plugin.yml");

        final JarResource pluginResource = containerMap.get("plugin.yml");
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pluginResource.getData())){
            final Yaml yaml = new Yaml();
            final Map<String, Object> config = yaml.load(inputStream);

            final String main = (String) config.get("main");

            System.out.println("Main name: " + main);
            String remapped = skidfuscator
                    .getClassRemapper()
                    .map(Type.getObjectType(main.replace(".", "/"))
                            .getInternalName());

            System.out.println("Remapped: " + remapped);

            config.put("main", remapped);

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                yaml.dump(config, new OutputStreamWriter(outputStream));
                pluginResource.setData(outputStream.toByteArray());
            }
        } catch (IOException e) {
            Skidfuscator.LOGGER.error("FAILED TO READ PLUGIN.YML", e);
        }
    }
}
