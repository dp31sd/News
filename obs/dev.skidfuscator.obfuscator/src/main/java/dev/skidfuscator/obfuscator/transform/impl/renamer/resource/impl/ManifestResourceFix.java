package dev.skidfuscator.obfuscator.transform.impl.renamer.resource.impl;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.transform.impl.renamer.resource.ResourceCheck;
import org.objectweb.asm.Type;
import org.topdank.byteengineer.commons.data.JarResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.Manifest;

public class ManifestResourceFix implements ResourceCheck {
    @Override
    public void fix(Skidfuscator skidfuscator) {
        final Map<String, JarResource> containerMap = skidfuscator
                .getJarContents()
                .getResourceContents()
                .namedMap();

        if (!containerMap.containsKey("META-INF/MANIFEST.MF")) {
            Skidfuscator.LOGGER.warn("Failed to find MANIFEST.MF! Skipping...");
            return;
        }

        final JarResource manifestResource = containerMap.get("META-INF/MANIFEST.MF");
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(manifestResource.getData())){
            final Manifest manifest = new Manifest(inputStream);
            System.out.println(Arrays.toString(manifest.getMainAttributes().keySet().toArray()));
            final String main = manifest.getMainAttributes().getValue("Main-Class");

            if (main == null) {
                Skidfuscator.LOGGER.warn("Failed to find main in MANIFEST.MF! Skipping...");
                return;
            }
            System.out.println("Main name: " + main);
            String remapped = skidfuscator
                    .getClassRemapper()
                    .map(Type.getObjectType(main.replace(".", "/")).getInternalName());

            System.out.println("Remapped: " + remapped);
            manifest.getMainAttributes().putValue(
                    "Main-Class",
                    remapped.replace("/", ".")
            );

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                manifest.write(outputStream);
                manifestResource.setData(outputStream.toByteArray());
            }
        } catch (IOException e) {
            Skidfuscator.LOGGER.error("FAILED TO READ MANIFEST", e);
        }
    }
}
