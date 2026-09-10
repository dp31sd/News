package dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.impl;

import dev.skidfuscator.obfuscator.transform.impl.renamer.dictionary.RenamerDictionary;

public class CustomUserDictionary implements RenamerDictionary {
    private final int depth;
    private final String[] chars;
    private int index;

    private boolean clazz;

    public CustomUserDictionary(int depth, String[] chars, boolean clazz) {
        this.depth = depth;
        this.chars = chars;
        this.clazz = clazz;
    }

    @Override
    public String next() {
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            int localIndex = index;
            do {
                builder.append(chars[localIndex % chars.length]);
                localIndex = (int) Math.floor((double) localIndex / chars.length);
            } while ((--localIndex) >= 0);

            index++;

            if (i + 1 == depth)
                continue;

            if (clazz)
                builder.append("/");
            else
                builder.append("$");
        }

        builder.reverse();

        return builder.toString();
    }

    @Override
    public void reset() {
        index = 0;
    }
}
