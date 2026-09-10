package dev.skidfuscator.obfuscator.predicate.cache;

import dev.skidfuscator.annotations.NativeObfuscation;
import dev.skidfuscator.obfuscator.transform.impl.j2c.Dispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CacheTemplate {
    private static Map<String, Integer> initialPredicates;

    @NativeObfuscation
    private static void add(final String cache, int predicate) {
        initialPredicates.put(cache, predicate);
    }

    @NativeObfuscation
    public static int get(final String clazz) {
        final int count = initialPredicates.get("count");
        final boolean match = count == Dispatcher.loaded;

        if (!match) {
            System.out.println("Unmatched: " + count + " vs " + Dispatcher.loaded);
            return new Random().nextInt();
        }

        initialPredicates.put("count", count + 1);
        return initialPredicates.get(clazz);
    }

    @NativeObfuscation
    private static void init() {
    }

    @NativeObfuscation
    private static void bootstrap() {
        initialPredicates = new HashMap<>();
        initialPredicates.put("count", 1);
    }

    static {
        bootstrap();
        init();
    }
}
