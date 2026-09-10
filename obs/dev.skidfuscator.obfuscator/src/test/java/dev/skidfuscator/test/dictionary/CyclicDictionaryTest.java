package dev.skidfuscator.test.dictionary;

import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;
import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl.CyclicAlphabetDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CyclicDictionaryTest {

    @Test
    public void testIteration() {
        final RenamerDictionary dictionary = new CyclicAlphabetDictionary();
        assertEquals("a", dictionary.next());
        assertEquals("b", dictionary.next());
        assertEquals("c", dictionary.next());

        int i = 49;
        while (i --> 0) {
            dictionary.next();
        }

        assertEquals("aa", dictionary.next());
        assertEquals("ab", dictionary.next());
        i = 50;
        while (i --> 0) {
            dictionary.next();
        }
        assertEquals("ba", dictionary.next());
        assertEquals("bb", dictionary.next());

        i = 50;
        while (i --> 0) {
            dictionary.next();
        }

        assertEquals("ca", dictionary.next());

        i = 52*50 - 1;
        while (i --> 0) {
            dictionary.next();
        }

        assertEquals("aaa", dictionary.next());
    }
}
