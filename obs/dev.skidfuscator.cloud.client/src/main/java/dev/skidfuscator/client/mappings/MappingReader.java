package dev.skidfuscator.client.mappings;

import dev.skidfuscator.jghost.Ghost;
import dev.skidfuscator.jghost.tree.GhostLibrary;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;

import java.io.File;

public interface MappingReader {
    GhostLibrary readMappingsAndMerge(final SkidfuscatorSession session);
}
