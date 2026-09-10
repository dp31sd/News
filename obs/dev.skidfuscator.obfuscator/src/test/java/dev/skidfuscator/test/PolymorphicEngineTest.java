package dev.skidfuscator.test;

import dev.skidfuscator.obfuscator.polymorphic.model.Context;
import dev.skidfuscator.obfuscator.polymorphic.model.PolymorphicEngine;
import dev.skidfuscator.obfuscator.polymorphic.transforms.RotateLeft;
import dev.skidfuscator.obfuscator.polymorphic.transforms.model.Transformation;
import dev.skidfuscator.obfuscator.polymorphic.visitors.JavaVisitor;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class PolymorphicEngineTest {
    @Test
    public void testRotate() {
        final PolymorphicEngine engine = new PolymorphicEngine(3, 8, 16);

        final Context context = engine.transform("test");
        final StringBuilder obfedString = new StringBuilder();
        for (long b : context.getBytes()) {
            obfedString.append((char) b);
        }
        final RotateLeft rotate = new RotateLeft(5L, 16);
        final long x = rotate.transform(10);
        final long reversed = rotate.reversed().transform(x);

        assert reversed == 10L : "Failed reverse check";

        System.out.println(obfedString);

        for (Transformation transformation : context.getReverse()) {
            System.out.println(transformation);
        }

        for (int i = 0; i < obfedString.length(); i++) {
            char ch = obfedString.charAt(i);

            for (Transformation transformation : context.getReverse()) {
                ch = (char) Integer.decode("0x"+ Long.toHexString(transformation.transform(ch))).intValue();
            }

            obfedString.setCharAt(i, ch);
        }

        System.out.println("Deobfed:" + obfedString);
        assert obfedString.toString().equals("test") : "Failed equality check";
    }
}
