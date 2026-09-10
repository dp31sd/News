package dev.skidfuscator.client.mappings.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.skidfuscator.client.Client;
import dev.skidfuscator.client.mappings.MappingReader;
import dev.skidfuscator.client.util.TimedLogger;
import dev.skidfuscator.config.DefaultSkidConfig;
import dev.skidfuscator.jghost.GhostHelper;
import dev.skidfuscator.jghost.merge.LibraryMerger;
import dev.skidfuscator.jghost.tree.GhostLibrary;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.LibraryClassSource;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class CommunityMappingReader implements MappingReader {
    private static final TimedLogger LOGGER = Client.LOGGER;

    @Override
    public GhostLibrary readMappingsAndMerge(SkidfuscatorSession session) {
        final LibraryMerger merger = new LibraryMerger();
        final AtomicInteger mergeCounter = new AtomicInteger();
        GhostLibrary parentLibrary = new GhostLibrary();

        /* Import JVM */
        LOGGER.post("Beginning importing of the JVM...");
        if (session.getRuntime() != null) {
            /*
             * Pardon my inverse condition, although the order will make sense in
             * a second. Before J9/J11, Java had all of its libraries compiled in
             * a single jar called rt.jar. This is no longer the case, although
             * since J8 is still the most predominantly used version of Java, it
             * is a no-brainer to support it.
             *
             * + I love J8,... death to "var" in Java
             */
            if (!session.isJmod()) {
                parentLibrary = GhostHelper.getLibrary(session.getRuntime(), true);
                LOGGER.post("✓ Success");
            }
            /*
             * The choice of JMod in Java is so odd. Same "zip" format as other Jars,
             * but completely and utterly discoostin. Oh well whatever. Here we try
             * to download these fancily to be able to resolve all the classes in
             * what used to be rt.jar.
             */
            else {
                for (File file : session.getRuntime().listFiles()) {
                    if (!file.getAbsolutePath().endsWith(".jmod"))
                        continue;

                    final GhostLibrary library = GhostHelper.getLibrary(file, true);
                    parentLibrary = merger.merge(parentLibrary, library);
                    LOGGER.post("   ✓ Merged successfully");
                }
                LOGGER.post("✓ Success");
            }
            LOGGER.log("Finished importing the JVM!");
        }

        if (session.getMappings() != null) {
            final File[] libs = Arrays.stream(session.getMappings().listFiles())
                    .filter(e -> e.getAbsolutePath().endsWith(".json"))
                    .toArray(File[]::new);

            LOGGER.post("Importing " + libs.length + " mappings...");

            for (File lib : libs) {
                final GhostLibrary library = GhostHelper.readFromLibraryFile(lib);
                parentLibrary = merger.merge(parentLibrary, library);
                LOGGER.post("Merging library [x" + mergeCounter.incrementAndGet() + "]");
            }
            LOGGER.log("✓ Finished importing mappings!");
        } else if (session.getLibs() != null && session.getLibs().length > 0) {

            final File[] libs = Arrays.stream(session.getLibs())
                    .filter(e -> e.getAbsolutePath().endsWith(".jar"))
                    .toArray(File[]::new);

            LOGGER.post("Importing " + libs.length + " libs...");

            for (File lib : libs) {
                final GhostLibrary library = GhostHelper.createFromLibraryFile(lib);
                parentLibrary = merger.merge(parentLibrary, library);
                LOGGER.post("Merging library [x" + mergeCounter.incrementAndGet() + "]");
            }
            LOGGER.log("✓ Finished importing libs!");
        }

        if (session.getConfig() != null) {
            final Config tsConfig = ConfigFactory.parseFile(session.getConfig()).resolve();
            final DefaultSkidConfig config = new DefaultSkidConfig(tsConfig, "");

            for (File lib : config.getLibs()) {
                final GhostLibrary library = GhostHelper.createFromLibraryFile(lib);
                parentLibrary = merger.merge(parentLibrary, library);
                LOGGER.post("Merging library [x" + mergeCounter.incrementAndGet() + "]");
            }

            LOGGER.log("✓ Finished importing config libs!");
        }

        return parentLibrary;
    }
}
