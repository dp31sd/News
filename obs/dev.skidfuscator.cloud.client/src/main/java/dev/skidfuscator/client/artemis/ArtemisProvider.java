package dev.skidfuscator.client.artemis;

import com.google.gson.Gson;
import dev.skidfuscator.client.artemis.logs.Log;
import dev.skidfuscator.client.artemis.logs.listener.LoggerListener;
import dev.skidfuscator.client.artemis.output.Output;
import dev.skidfuscator.client.artemis.output.listener.OutputListener;
import dev.skidfuscator.client.connector.ConnectedInstance;
import dev.skidfuscator.client.connector.Connector;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;

public interface ArtemisProvider {
    SkidfuscatorSession config();

    Gson gson();

    ConnectedInstance instance();

    void addLogListener(final LoggerListener listener);

    void addOutputListener(final OutputListener listener);

    void distribute(final Log log);

    void distribute(final Output output);

    interface Factory {
        Factory config(final SkidfuscatorSession session);

        Factory gson(final Gson gson);

        Factory instance(final ConnectedInstance instance);

        Factory simpleLogListener();

        Factory simpleOutputListener();

        Factory addLogListener(final LoggerListener... listeners);

        Factory addOutputListener(final OutputListener... listeners);

        ArtemisProvider build();
    }
}
