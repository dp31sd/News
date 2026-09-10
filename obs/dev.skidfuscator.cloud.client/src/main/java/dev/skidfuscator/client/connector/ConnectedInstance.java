package dev.skidfuscator.client.connector;

import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;

public interface ConnectedInstance {
    void listen(final ArtemisProvider provider);

    void publish(final SkidfuscatorSession session);

    void shutdown();
}
