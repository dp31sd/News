package dev.skidfuscator.client.connector.impl;

import dev.skidfuscator.client.Client;
import dev.skidfuscator.client.connector.ConnectedInstance;
import dev.skidfuscator.client.connector.Connector;
import org.eclipse.jetty.websocket.jsr356.ClientContainer;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.frame.Jackson2SockJsMessageCodec;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class StompConnector implements Connector {
    private final static WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

    @Override
    public ConnectedInstance connect(String url, int port) {
        final ClientContainer container = (ClientContainer) ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(100 * 1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(100 * 1024 * 1024);

        Client.SCHEDULER = new ThreadPoolTaskScheduler();
        Client.SCHEDULER.setPoolSize(1);
        Client.SCHEDULER.initialize();

        final StandardWebSocketClient transport = new StandardWebSocketClient(container);
        transport.setTaskExecutor(Client.SCHEDULER);

        final WebSocketTransport webSocketTransport = new WebSocketTransport(transport);
        List<Transport> transports = Collections.singletonList(webSocketTransport);

        final SockJsClient sockJsClient = new SockJsClient(transports);
        sockJsClient.setMessageCodec(new Jackson2SockJsMessageCodec());

        final WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setInboundMessageSizeLimit(100 * 1024 * 1024);
        //stompClient.setSendBufferSizeLimit(1024 * 1024);
        //stompClient.setSendTimeLimit(20000);

        stompClient.setTaskScheduler(Client.SCHEDULER);

        final StompSession session;

        try {
            session = stompClient.connect(
                    "ws://{host}:{port}/ws",
                    headers,
                    new Adapter(),
                    url,
                    port
            ).completable().exceptionally(new Function<Throwable, StompSession>() {
                @Override
                public StompSession apply(Throwable throwable) {
                    throwable.printStackTrace();
                    return null;
                }
            }).get(/*10, TimeUnit.SECONDS*/);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to connect to Skidfuscator Backend", e);
        }

        return new StompConnectedInstance(stompClient, container, session, new Runnable() {
            @Override
            public void run() {
                webSocketTransport.stop();
                sockJsClient.stop();
                container.setStopTimeout(1000);
                try {
                    container.stop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static class Adapter extends StompSessionHandlerAdapter {
        public void afterConnected(
                final StompSession stompSession,
                final StompHeaders stompHeaders) {
            System.out.println("Now connected");
        }
    }
}
