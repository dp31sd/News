package dev.skidfuscator.client.artemis;

public interface Listener<T> {
    void handle(T t);
}
