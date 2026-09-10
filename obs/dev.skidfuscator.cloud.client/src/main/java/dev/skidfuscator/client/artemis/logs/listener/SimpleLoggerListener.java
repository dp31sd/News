package dev.skidfuscator.client.artemis.logs.listener;

import dev.skidfuscator.client.artemis.AbstractArtemisProvidable;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.client.artemis.logs.Log;
import org.apache.log4j.LogManager;
import org.apache.log4j.Priority;

public class SimpleLoggerListener extends AbstractArtemisProvidable implements LoggerListener {
    public SimpleLoggerListener(ArtemisProvider provider) {
        super(provider);
    }

    @Override
    public void handle(Log log) {
        final Priority priority = log.getSeverity().toApacheLog4j();
        LogManager.getRootLogger().log(priority, log.getMessage());
    }
}
