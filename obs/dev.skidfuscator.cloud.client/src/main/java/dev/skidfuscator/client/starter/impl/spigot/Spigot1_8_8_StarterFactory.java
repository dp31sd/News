package dev.skidfuscator.client.starter.impl.spigot;

import dev.skidfuscator.client.starter.StarterSession;
import dev.skidfuscator.client.starter.impl.forge.AbstractForgeStarterFactory;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class Spigot1_8_8_StarterFactory extends AbstractForgeStarterFactory {
    public Spigot1_8_8_StarterFactory() {
        super("Spigot 1.8.8");
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

        // Download spigot api
        System.out.println("Downloading spigot...");
        final File forge = new File(mappings, "spigot.json");
        this.downloadMappings(
                forge,
                "https://raw.githubusercontent.com/skidfuscatordev/mappings/main/spigot/1.8.8/spigot-1.8.8.jar.json"
        );

        // TODO: Add exemptions for spigot dependencies

        System.out.println("Success!");
    }
}
