package dev.skidfuscator.backend.service;

import dev.skidfuscator.protocol.SkidSessionData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ObfuscatorConsumer {
    private boolean renamer;
    private byte[] config;
    private final ByteArrayOutputStream jar;
    private final ByteArrayOutputStream mappings;

    public ObfuscatorConsumer(SkidSessionData config) {
        this.config = config.getConfig();
        this.renamer = config.isRenamer();
        this.jar = new ByteArrayOutputStream();
        this.mappings = new ByteArrayOutputStream();
    }

    public byte[] getConfig() {
        return config;
    }

    public void setConfig(byte[] config) {
        this.config = config;
    }

    public byte[] getJar() {
        return jar.toByteArray();
    }

    public void addJar(byte[] jar) throws IOException {
        this.jar.write(jar);
    }

    public byte[] getMappings() {
        return mappings.toByteArray();
    }

    public void addMappings(byte[] mappings) throws IOException {
        this.mappings.write(mappings);
    }

    public boolean isRenamer() {
        return renamer;
    }

    public void setRenamer(boolean renamer) {
        this.renamer = renamer;
    }
}
