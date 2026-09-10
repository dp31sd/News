package dev.skidfuscator.test;

import com.esotericsoftware.asm.Type;
import dev.skidfuscator.core.classloader.SkidClassLoader;
import dev.skidfuscator.j2c.JavaToC;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Ghast
 * @since 06/03/2021
 * SkidfuscatorV2 © 2021
 */

@Disabled("Native is b0rken")
public class NativeSampleJarTest {

    @Test
    public void test() throws Exception {
        JavaToC.FORCE = true;
        final File input = new File("src/test/resources/test.jar");
        final File output = new File("src/test/resources/test-out-native.jar");

        final File runtime = new File(
                new File(System.getProperty("java.home")),
                MiscUtil.getJavaVersion() > 8
                        ? "jmods"
                        : "lib/rt.jar"
        );

        final SkidfuscatorSession session = SkidfuscatorSession
                .builder()
                .input(input)
                .output(output)
                .runtime(runtime)
                .jmod(MiscUtil.getJavaVersion() > 8)
                .phantom(true)
                .analytics(false)
                .c2j(true)
                .build();

        final Skidfuscator skidfuscator = new Skidfuscator(session);
        skidfuscator.run();
        JavaToC.FORCE = false;

        final URL[] urls = new URL[]{
                output.toURL()
        };

        for (URL url : urls) {
            try (JarInputStream jarInputStream = new JarInputStream(url.openStream())) {
                JarEntry entry;
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    System.out.println(entry.getName());
                }
            }

        }

        try(SkidClassLoader classLoader = new SkidClassLoader(urls)) {
            final String name = skidfuscator
                    .getClassRemapper()
                    .map(Type.getObjectType("dev/sim0n/evaluator/Main").getInternalName());
            final String replaced = name == null ? "dev.sim0n.evaluator.Main" : name.replace("/", ".");
            final Class<?> clazz = classLoader.loadClass(replaced);
            clazz.getDeclaredMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }

    }
}
