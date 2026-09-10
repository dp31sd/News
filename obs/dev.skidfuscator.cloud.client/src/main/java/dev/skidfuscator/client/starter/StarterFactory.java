package dev.skidfuscator.client.starter;

public interface StarterFactory {
    /**
     * @return Returns the name for the Starter factory
     */
    String getName();

    /**
     * Creates a fresh new starter pack for obfuscation. A bit like NPM
     * init but for Skidfuscator. Kinda cool ay?
     * @param session Session details with directory for output
     */
    void createStart(final StarterSession session);
}
