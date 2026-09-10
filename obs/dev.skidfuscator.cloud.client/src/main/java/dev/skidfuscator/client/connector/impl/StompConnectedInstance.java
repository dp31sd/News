package dev.skidfuscator.client.connector.impl;

import com.google.gson.Gson;
import dev.skidfuscator.client.Client;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.client.artemis.logs.LogHandler;
import dev.skidfuscator.client.artemis.output.OutputHandler;
import dev.skidfuscator.client.connector.ConnectedInstance;
import dev.skidfuscator.client.mappings.impl.CommunityMappingReader;
import dev.skidfuscator.client.util.ProgressUtil;
import dev.skidfuscator.inflator.SkidInflator;
import dev.skidfuscator.jghost.GhostHelper;
import dev.skidfuscator.jghost.tree.GhostLibrary;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import dev.skidfuscator.protocol.SkidSessionData;
import dev.skidfuscator.protocol.SkidSessionDocumentData;
import dev.skidfuscator.protocol.request.AuthenticateRequest;
import dev.skidfuscator.protocol.request.DataRequest;
import dev.skidfuscator.protocol.request.ExecuteRequest;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.websocket.WebSocketContainer;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StompConnectedInstance implements ConnectedInstance {
    private final WebSocketStompClient client;
    private final WebSocketContainer container;
    private final StompSession session;

    private final Runnable shutdown;
    private Gson gson;


    public StompConnectedInstance(WebSocketStompClient client, WebSocketContainer container, StompSession session, Runnable shutdown) {
        this.client = client;
        this.container = container;
        this.session = session;
        this.shutdown = shutdown;
    }

    @Override
    public void listen(ArtemisProvider provider) {
        this.gson = provider.gson();

        session.setAutoReceipt(true);
        session.subscribe(
                "/user/skid/logs",
                new LogHandler(provider)
        );
        session.subscribe(
                "/user/skid/output",
                new OutputHandler(provider)
        );
    }

    @Override
    public void publish(SkidfuscatorSession session) {
        final SkidSessionData skidSessionData = new SkidSessionData();

        /* Config handling */
        final byte[] config;
        if (session.getConfig() != null) {
            try {
                config = FileUtils.readFileToByteArray(session.getConfig());
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to read exemptions (0x03)", e);
            }
        } else {
            config = new byte[0];
        }

        Client.LOGGER.log("✓ Finished compressing the config");

        skidSessionData.setConfig(config);
        skidSessionData.setRenamer(session.isRenamer());
        final String jsonInit = gson.toJson(
                new AuthenticateRequest("sk1d123456789!_vaziak_bad_E", skidSessionData),
                AuthenticateRequest.class
        );

        this.session.send("/skid/init", jsonInit.getBytes());

        Client.LOGGER.log("✓ Finished initialization");

        final File compressedJar = new File(
                session.getInput().getAbsolutePath() + ".compressed"
        );

        try {
            final byte[] libBytes = FileUtils.readFileToByteArray(session.getInput());
            final byte[] compressedBytes = SkidInflator.compress(libBytes);
            FileUtils.writeByteArrayToFile(compressedJar, compressedBytes);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to compress jar (0x01)", e);
        }

        /* File handling */
        if (session.isLowCon()) {
            send(compressedJar, "/skid/jar");
        } else {
            try {
                final byte[] data = FileUtils.readFileToByteArray(compressedJar);
                final String jsonJar = gson.toJson(
                        new DataRequest(new SkidSessionDocumentData(data)),
                        DataRequest.class
                );
                final Lock lock = new ReentrantLock();
                final StompSession.Receiptable receiptable = this.session.send(
                        "/skid/jar", jsonJar.getBytes()
                );
                receiptable.addReceiptTask(lock::unlock);
                receiptable.addReceiptLostTask(() -> {
                    throw new IllegalStateException("Failed sending!");
                });
                lock.lock();
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to upload to Skidfuscator Backend (0x01)", e);
            }
        }

        Client.LOGGER.log("✓ Finished publishing the jar");
        Client.LOGGER.log("");

        /* Mappings handling */
        final File compressed = new File(
                session.getInput().getAbsolutePath() + ".compressed.skid"
        );
        try {
            final GhostLibrary library = new CommunityMappingReader()
                    .readMappingsAndMerge(session);

            final byte[] libBytes = GhostHelper.serializeLibraryFile(library);
            final byte[] compressedBytes = SkidInflator.compress(libBytes);
            FileUtils.writeByteArrayToFile(compressed, compressedBytes);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to compress mappings (0x02)", e);
        }

        Client.LOGGER.log("✓ Finished compressing the mappings");
        if (session.isLowCon()) {
            send(compressed, "/skid/mappings");
        } else {
            try {
                final byte[] data = FileUtils.readFileToByteArray(compressed);
                final String jsonJar = gson.toJson(
                        new DataRequest(new SkidSessionDocumentData(data)),
                        DataRequest.class
                );
                final Lock lock = new ReentrantLock();
                final StompSession.Receiptable receiptable = this.session.send(
                        "/skid/mappings", jsonJar.getBytes()
                );
                receiptable.addReceiptTask(lock::unlock);
                receiptable.addReceiptLostTask(() -> {
                    throw new IllegalStateException("Failed sending!");
                });
                lock.lock();
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to upload to Skidfuscator Backend (0x02)", e);
            }
        }

        Client.LOGGER.log("✓ Finished publishing the mappings");

        /* Final handling */
        final String jsonHello = gson.toJson(
                new DataRequest(true),
                DataRequest.class
        );

        final Lock lock = new ReentrantLock();
        final StompSession.Receiptable receiptable = this.session.send(
                "/skid/mappings", jsonHello.getBytes()
        );
        receiptable.addReceiptTask(lock::unlock);
        receiptable.addReceiptLostTask(lock::unlock);
        lock.lock();
        Client.LOGGER.log("✓ Signaled execution");
    }

    private void send(final File file, String endpoint) {
        try (final FileInputStream in = FileUtils.openInputStream(file)) {
            try (final ProgressBar progressBar = ProgressUtil.progress(in.available())){
                final byte[] buffer = new byte[108000];
                int rc = 0;

                do {
                    rc = in.read(buffer, 0, Math.min(buffer.length, in.available()));
                    // rc should contain the number of bytes read in this operation.
                    // do stuff...
                    //System.out.println("Sending mappings buffer of size " + rc);
                    final String jsonJar = gson.toJson(
                            new DataRequest(new SkidSessionDocumentData(buffer)),
                            DataRequest.class
                    );
                    final Lock lock = new ReentrantLock();
                    final StompSession.Receiptable receiptable = this.session.send(
                            endpoint, jsonJar.getBytes()
                    );
                    receiptable.addReceiptTask(lock::unlock);
                    receiptable.addReceiptLostTask(lock::unlock);
                    lock.lock();
                    progressBar.stepBy(rc);
                    // next read
                } while (rc == buffer.length);
            }

        } catch (Throwable e) {
            throw new IllegalStateException("Failed to upload to Skidfuscator Backend (0x01)", e);
        }
    }

    @Override
    public void shutdown() {
        System.out.println("Shutting down...");

        this.session.setAutoReceipt(false);
        System.out.println("Disabling receipts...");

        this.session.disconnect();
        System.out.println("Disconnecting...");

        this.client.stop();
        System.out.println("Stopping client...");

        Client.SCHEDULER.shutdown();
        System.out.println("Turning off scheduler...");

        try {
            shutdown.run();
            System.out.println("Killing lifecycle...");

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Goodbye!");
    }
}
