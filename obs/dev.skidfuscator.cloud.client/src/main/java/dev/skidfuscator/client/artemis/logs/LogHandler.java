package dev.skidfuscator.client.artemis.logs;

import dev.skidfuscator.client.artemis.AbstractArtemisProvidable;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.protocol.response.LogResponse;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.reflect.Type;

public class LogHandler extends AbstractArtemisProvidable implements StompFrameHandler {
    public LogHandler(ArtemisProvider provider) {
        super(provider);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        final String json = new String((byte[]) payload);
        final LogResponse response = provider.gson().fromJson(json, LogResponse.class);

        this.provider.distribute(new Log(
                response.getSeverity(),
                response.getMessage()
        ));
    }
}
