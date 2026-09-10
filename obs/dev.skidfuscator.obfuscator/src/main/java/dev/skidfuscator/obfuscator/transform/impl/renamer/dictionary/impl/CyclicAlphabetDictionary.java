package dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl;

import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;

public class CyclicAlphabetDictionary implements RenamerDictionary {
    private static final char[] letters = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    private int index;

    @Override
    public String next() {
        final StringBuilder builder = new StringBuilder();

        int localIndex = index;

        do {
            builder.append(letters[localIndex % letters.length]);
            localIndex = (int) Math.floor((double) localIndex / letters.length);
        } while ((--localIndex) >= 0);

        index++;
        builder.reverse();

        return builder.toString();
    }

    @Override
    public void reset() {
        index = 0;
    }
}
