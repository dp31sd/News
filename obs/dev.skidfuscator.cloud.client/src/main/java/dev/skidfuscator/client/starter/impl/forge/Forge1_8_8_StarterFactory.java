package dev.skidfuscator.client.starter.impl.forge;

import dev.skidfuscator.client.starter.StarterSession;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class Forge1_8_8_StarterFactory extends AbstractForgeStarterFactory {
    public Forge1_8_8_StarterFactory() {
        super("Forge 1.8.8");
    }

    @Override
    @SneakyThrows
    public void createStart(StarterSession session) {
        final File file = session.getDirectory();

        System.out.println("Creating at " + file.getAbsolutePath());

        // Ensure directory is created
        if (!file.exists()) {
            FileUtils.forceMkdir(file);
            FileUtils.forceMkdirParent(file);
        }

        final File mappings = new File(file, "mappings");

        // Download forge api
        final File forge = new File(mappings, "forge.json");

        System.out.println("Downloading forge...");
        this.downloadMappings(
                forge,
                "https://raw.githubusercontent.com/skidfuscatordev/mappings/main/forge/1.8.8/ForgeGradle-2.1-20210507.224146-40.jar.json"
        );

        // Download minecraft
        final File minecraft = new File(mappings, "minecraft.json");
        System.out.println("Downloading minecraft...");
        this.downloadMappings(
                minecraft,
                "https://raw.githubusercontent.com/skidfuscatordev/mappings/main/forge/1.8.8/minecraft-forge-1.8.8.jar.json"
        );

        System.out.println("Success!");
    }
}
