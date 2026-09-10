package dev.skidfuscator.client.artemis.output.listener;

import dev.skidfuscator.client.artemis.AbstractArtemisProvidable;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.client.artemis.logs.Log;
import dev.skidfuscator.client.artemis.output.Output;
import dev.skidfuscator.protocol.modal.Severity;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class SimpleOutputListener extends AbstractArtemisProvidable implements OutputListener {
    public SimpleOutputListener(ArtemisProvider provider) {
        super(provider);
    }

    @Override
    public void handle(Output output) {
        if (output.getFile().length == 0) {
            provider.distribute(new Log(
                    Severity.FATAL,
                    "Failed to obfuscate... Please contact support"
            ));
            provider.instance().shutdown();
            return;
        }

        final File jar = provider.config().getOutput();
        try {
            FileUtils.writeByteArrayToFile(jar, output.getFile());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        provider.distribute(new Log(
                Severity.INFO,
                "Finished copying file to path " + jar.getPath()
        ));

        provider.instance().shutdown();
    }
}
