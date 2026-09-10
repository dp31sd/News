package dev.skidfuscator.backend.runner;

import dev.skidfuscator.backend.obfuscator.ObfuscatorSession;
import dev.skidfuscator.obfuscator.Skidfuscator;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class ObfuscatorRunner implements Consumer<ObfuscatorSession> {
    @Override
    public void accept(ObfuscatorSession obfuscatorSession) {
        try {
            final Skidfuscator skidfuscator = new Skidfuscator(obfuscatorSession.getSession());
            skidfuscator.run();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        System.out.println("---- EOF ----");
    }
}
