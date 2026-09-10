package dev.skidfuscator.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.skidfuscator.backend.obfuscator.ObfuscatorQueue;
import dev.skidfuscator.backend.obfuscator.ObfuscatorSession;
import dev.skidfuscator.backend.runner.ObfuscatorRunner;
import dev.skidfuscator.backend.session.ObfuscatorUserSession;
import dev.skidfuscator.inflator.SkidInflator;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import dev.skidfuscator.obfuscator.util.MiscUtil;
import dev.skidfuscator.protocol.SkidSessionData;
import dev.skidfuscator.protocol.modal.Severity;
import dev.skidfuscator.protocol.response.LogResponse;
import dev.skidfuscator.protocol.response.ObfuscateResponse;
import dev.skidfuscator.protocol.response.TextResponse;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullPrintStream;
import org.apache.log4j.*;
import org.apache.log4j.spi.LoggingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;

@Service
public class ObfuscatorServiceImpl implements ObfuscatorService, Consumer<ObfuscatorSession> {
    @Autowired
    private SimpMessagingTemplate stompClient;

    private final Cache<ObfuscatorUserSession, ObfuscatorConsumer> sessionDataCache = CacheBuilder
            .newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private final ObfuscatorQueue queue = new ObfuscatorQueue(this);

    @Override
    public void create(ObfuscatorUserSession userSession, SkidSessionData session) {
        sessionDataCache.put(userSession, new ObfuscatorConsumer(session));
    }

    @Override
    @SneakyThrows
    public void addFile(ObfuscatorUserSession userSession, byte[] data) {
        sessionDataCache.getIfPresent(userSession).addJar(data);
    }

    @Override
    @SneakyThrows
    public void addMapping(ObfuscatorUserSession userSession, byte[] data) {
        sessionDataCache.getIfPresent(userSession).addMappings(data);
    }

    @Override
    public void execute(ObfuscatorUserSession userSession) throws DataFormatException, IOException {
        System.out.println("Getting data cache...");
        final ObfuscatorConsumer consumer = sessionDataCache.getIfPresent(userSession);

        System.out.println("Creating session dir...");
        final File tempDev = new File("sessions", userSession.getName() + "-session-" + UUID.randomUUID());
        tempDev.getParentFile().mkdirs();
        tempDev.mkdir();

        final File unobfFile = new File(tempDev, "unobf.jar");
        final File obfFile = new File(tempDev, "obf.jar");

        System.out.println("Writing jar");
        FileUtils.writeByteArrayToFile(
                unobfFile,
                SkidInflator.decompress(consumer.getJar())
        );

        System.out.println("Writing exempt...");
        final File configFile = new File(tempDev, "config.hocon");
        FileUtils.writeByteArrayToFile(
                configFile,
                consumer.getConfig()
        );

        System.out.println("Writing mappings...");
        final File mappingsFile = new File(tempDev, "mappings");
        FileUtils.writeByteArrayToFile(
                new File(mappingsFile, "packed.json"),
                SkidInflator.decompress(consumer.getMappings())
        );

        final String home = System.getProperty("java.home");
        final File runtime = new File(
                home,
                MiscUtil.getJavaVersion() > 8
                        ? "jmods"
                        : "lib/rt.jar"
        );

        final SkidfuscatorSession skidfuscatorSession = SkidfuscatorSession.builder()
                .input(unobfFile)
                .runtime(runtime)
                .mappings(mappingsFile)
                .output(obfFile)
                .config(configFile)
                .renamer(consumer.isRenamer())
                .phantom(false)
                .jmod(MiscUtil.getJavaVersion() > 8)
                .fuckit(false)
                .build();

        System.out.println("Writing queue...");
        queue.add(new ObfuscatorSession(
                userSession,
                skidfuscatorSession
        ));
    }

    @Override
    @SneakyThrows
    public void accept(ObfuscatorSession obfuscatorSession) {
        stompClient.convertAndSendToUser(
                obfuscatorSession.getUser().getName(),
                "skid/logs",
                TextResponse.success(">>>>>>>>>>>>>>>> Beginning session >>>>>>>>>>>>>>>> ")
        );

        final SkidfuscatorSession session = obfuscatorSession.getSession();

        final Appender appender = new AsyncAppender() {
            @Override
            public void append(LoggingEvent event) {
                stompClient.convertAndSendToUser(
                        obfuscatorSession.getUser().getName(),
                        "skid/logs",
                        new LogResponse(
                                Severity.fromApacheLog4j(event.getLevel()),
                                event.getRenderedMessage()
                        )
                );
            }
        };

        /* Begin of logging */
        LogManager.getRootLogger().addAppender(appender);

        final Skidfuscator skidfuscator = new Skidfuscator(
                obfuscatorSession.getSession()
        );
        Skidfuscator.CLOUD = true;
        boolean success = true;
        try {
            skidfuscator.run();
        } catch (Exception e) {
            success = false;
            Skidfuscator.LOGGER.error("Failed to obfuscate", e);
        }

        for (File file : session.getMappings().listFiles()) {
            file.delete();
        }
        //session.getMappings().delete();
        //session.getExempt().delete();
        //session.getInput().delete();

        final ObfuscateResponse response = new ObfuscateResponse();
        response.setData(success
                 ? FileUtils.readFileToByteArray(session.getOutput())
                 : new byte[0]
        );

        /* End of logging */
        LogManager.getRootLogger().removeAppender(appender);

        stompClient.setSendTimeout(5000);
        /* Final output */
        stompClient.convertAndSendToUser(
                obfuscatorSession.getUser().getName(),
                "skid/logs",
                TextResponse.success("Successfully obfuscated niche!")
        );

        stompClient.convertAndSendToUser(obfuscatorSession.getUser().getName(), "skid/output", response, new MessagePostProcessor() {
            @Override
            public Message<?> postProcessMessage(Message<?> message) {
                System.out.println("Successfully converted message");
                return message;
            }
        });
        stompClient.convertAndSendToUser(
                obfuscatorSession.getUser().getName(),
                "skid/logs",
                TextResponse.success("Successfully obfuscated software")
        );
        System.out.println("Sent to user!");
        //session.getOutput().delete();
    }
}
