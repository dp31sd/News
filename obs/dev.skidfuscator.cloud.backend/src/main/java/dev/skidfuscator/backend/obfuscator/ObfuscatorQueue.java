package dev.skidfuscator.backend.obfuscator;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class ObfuscatorQueue extends LinkedBlockingQueue<ObfuscatorSession> {
    private final Consumer<ObfuscatorSession> poller;
    private final ExecutorService service = Executors.newSingleThreadExecutor();

    public ObfuscatorQueue(Consumer<ObfuscatorSession> poller) {
        this.poller = poller;
    }

    @Override
    public boolean add(ObfuscatorSession obfuscatorRequest) {
        if (isEmpty()) {
            kewl(obfuscatorRequest);
            return true;
        } else {
            return super.add(obfuscatorRequest);
        }
    }

    private void kewl(final ObfuscatorSession obfuscatorRequest) {
        service.execute(() -> {
            try {
                poller.accept(obfuscatorRequest);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            remove(obfuscatorRequest);

            if (!isEmpty()) {
                kewl(peek());
            }
        });
    }

    @Override
    public boolean offer(ObfuscatorSession obfuscatorRequest) {
        final boolean valid = super.offer(obfuscatorRequest);
        return valid;
    }

    @Override
    public ObfuscatorSession poll() {
        return super.poll();
    }

    @Override
    public ObfuscatorSession take() throws InterruptedException {
        return super.take();
    }

    @Override
    public ObfuscatorSession peek() {
        return super.peek();
    }

    @Override
    public boolean remove(Object o) {
        return super.remove(o);
    }
}