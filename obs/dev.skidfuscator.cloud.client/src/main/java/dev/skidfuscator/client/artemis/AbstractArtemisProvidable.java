package dev.skidfuscator.client.artemis;

public abstract class AbstractArtemisProvidable {
    protected final ArtemisProvider provider;

    public AbstractArtemisProvidable(ArtemisProvider provider) {
        this.provider = provider;
    }
}
