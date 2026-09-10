package dev.skidfuscator.client.connector;

public interface Connector {
    ConnectedInstance connect(final String url, final int port);
}
