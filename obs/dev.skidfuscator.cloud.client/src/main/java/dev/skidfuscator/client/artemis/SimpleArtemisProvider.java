package dev.skidfuscator.client.artemis;

import com.google.gson.Gson;
import dev.skidfuscator.client.artemis.logs.Log;
import dev.skidfuscator.client.artemis.logs.listener.LoggerListener;
import dev.skidfuscator.client.artemis.logs.listener.SimpleLoggerListener;
import dev.skidfuscator.client.artemis.output.Output;
import dev.skidfuscator.client.artemis.output.listener.OutputListener;
import dev.skidfuscator.client.artemis.output.listener.SimpleOutputListener;
import dev.skidfuscator.client.connector.ConnectedInstance;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class SimpleArtemisProvider implements ArtemisProvider {
    private final SkidfuscatorSession session;
    private final Gson gson;
    private final ConnectedInstance instance;

    private final List<LoggerListener> loggers;
    private final List<OutputListener> outputs;

    @Override
    public SkidfuscatorSession config() {
        return session;
    }

    @Override
    public Gson gson() {
        return gson;
    }

    @Override
    public ConnectedInstance instance() {
        return instance;
    }

    @Override
    public void distribute(Log log) {
        for (LoggerListener logger : loggers) {
            logger.handle(log);
        }
    }

    @Override
    public void distribute(Output output) {
        for (OutputListener outputListener : outputs) {
            outputListener.handle(output);
        }
    }

    @Override
    public void addLogListener(LoggerListener listener) {
        this.loggers.add(listener);
    }

    @Override
    public void addOutputListener(OutputListener listener) {
        this.outputs.add(listener);
    }

    public static class Factory implements ArtemisProvider.Factory {
        private SkidfuscatorSession session;
        private Gson gson;
        private ConnectedInstance instance;
        private final List<LoggerListener> loggers = new ArrayList<>();
        private final List<OutputListener> outputs = new ArrayList<>();

        private boolean addLoggerSimple;
        private boolean addOutputSimple;

        @Override
        public ArtemisProvider.Factory config(SkidfuscatorSession session) {
            this.session = session;
            return this;
        }

        @Override
        public ArtemisProvider.Factory gson(Gson gson) {
            this.gson = gson;
            return this;
        }

        @Override
        public ArtemisProvider.Factory instance(ConnectedInstance instance) {
            this.instance = instance;
            return this;
        }

        @Override
        public ArtemisProvider.Factory simpleLogListener() {
            this.addLoggerSimple = true;
            return this;
        }

        @Override
        public ArtemisProvider.Factory simpleOutputListener() {
            this.addOutputSimple = true;
            return this;
        }

        @Override
        public ArtemisProvider.Factory addLogListener(LoggerListener... listeners) {
            this.loggers.addAll(Arrays.asList(listeners));
            return this;
        }

        @Override
        public ArtemisProvider.Factory addOutputListener(OutputListener... listeners) {
            this.outputs.addAll(Arrays.asList(listeners));
            return this;
        }

        @Override
        public ArtemisProvider build() {
            final ArtemisProvider provider = new SimpleArtemisProvider(
                    session,
                    gson,
                    instance,
                    loggers,
                    outputs
            );

            if (addLoggerSimple) {
                provider.addLogListener(new SimpleLoggerListener(provider));
            }

            if (addOutputSimple) {
                provider.addOutputListener(new SimpleOutputListener(provider));
            }

            return provider;
        }
    }
}
