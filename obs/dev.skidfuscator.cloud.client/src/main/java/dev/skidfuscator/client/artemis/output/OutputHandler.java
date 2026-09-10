package dev.skidfuscator.client.artemis.output;

import dev.skidfuscator.client.artemis.AbstractArtemisProvidable;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.protocol.response.ObfuscateResponse;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.reflect.Type;

public class OutputHandler extends AbstractArtemisProvidable implements StompFrameHandler {
    public OutputHandler(ArtemisProvider provider) {
        super(provider);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        final String json = new String((byte[]) payload);
        final ObfuscateResponse response = provider.gson().fromJson(json, ObfuscateResponse.class);

        provider.distribute(new Output(
                "output.jar",
                response.getData()
        ));
    }
}
