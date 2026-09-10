package dev.skidfuscator.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skidfuscator.inflator.SkidInflator;
import dev.skidfuscator.protocol.request.AuthenticateRequest;
import dev.skidfuscator.protocol.SkidSessionData;
import dev.skidfuscator.protocol.request.TestRequest;
import dev.skidfuscator.protocol.response.ObfuscateResponse;
import dev.skidfuscator.protocol.response.TextResponse;
import org.apache.commons.io.FileUtils;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.concurrent.ListenableFuture;
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
import java.io.File;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ClientTest {
    public static void main(final String[] args) throws URISyntaxException {
        new ClientTest(new URI("ws://localhost:8080/ws/user/test"));
        System.out.println("EEEEEE");
    }

    private final Gson gson = new GsonBuilder()
            .create();

    public ClientTest(URI serverUri) {
        try {
            ListenableFuture<StompSession> f = this.connect();
            StompSession stompSession = f.get();

            ListenableFuture<StompSession> f2 = this.connect();
            StompSession stompSessio2n = f2.get();

            System.out.println("Subscribing to greeting topic using session " + stompSession);
            this.subscribeGreetings(stompSession, "Stomp #1");
            this.subscribeGreetings(stompSessio2n, "Stomp #2");

            System.out.println("Sending hello message" + stompSession);
            this.sendHello(stompSession);

            System.out.println("Sending payload message");
            final SkidSessionData skidSessionData = new SkidSessionData();
            final byte[] file = FileUtils.readFileToByteArray(new File("dev.skidfuscator.obfuscator.cloud/client-cloud/src/main/resources/test.jar"));
            final byte[] mappings = SkidInflator.compress(
                    FileUtils.readFileToByteArray(
                            new File("dev.skidfuscator.obfuscator.cloud/client-cloud/src/main/resources/rt.jar.json")
                    )
            );
            final byte[] config = new byte[0];

            //skidSessionData.setDocument(file);
            //skidSessionData.setMappings(mappings);
            skidSessionData.setConfig(config);

            subscribeLogging(stompSession, "Obfuscator");
            System.out.println("Sending...");
            sendThing(stompSession, skidSessionData);
            System.out.println("Sent!");

            Thread.sleep(60000);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private final static WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    public ListenableFuture<StompSession> connect() {
        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(100 * 1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(100 * 1024 * 1024);

        final WebSocketClient transport = new StandardWebSocketClient(container);
        final Transport webSocketTransport = new WebSocketTransport(transport);
        List<Transport> transports = Collections.singletonList(webSocketTransport);

        final SockJsClient sockJsClient = new SockJsClient(transports);
        sockJsClient.setMessageCodec(new Jackson2SockJsMessageCodec());

        final WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setInboundMessageSizeLimit(100 * 1024 * 1024);
        //stompClient.setSendBufferSizeLimit(1024 * 1024);
        //stompClient.setSendTimeLimit(20000);

        String url = "ws://{host}:{port}/ws";
        return stompClient.connect(url, headers, new MyHandler(), "localhost", 8080);
    }

    private class MyHandler extends StompSessionHandlerAdapter {
        public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
            System.out.println("Now connected");
        }
    }

    public void subscribeLogging(StompSession stompSession, final String name) throws ExecutionException, InterruptedException {
        stompSession.subscribe("/user/skid/logs", new StompFrameHandler() {

            public Type getPayloadType(StompHeaders stompHeaders) {
                return byte[].class;
            }

            public void handleFrame(StompHeaders stompHeaders, Object o) {
                final String json = new String((byte[]) o);
                final TextResponse response = gson.fromJson(json, TextResponse.class);
                System.out.println("[ " + name  + " ] >> " + response.getText());
            }
        });

        stompSession.subscribe("/user/skid/output", new StompFrameHandler() {

            public Type getPayloadType(StompHeaders stompHeaders) {
                return byte[].class;
            }

            public void handleFrame(StompHeaders stompHeaders, Object o) {
                System.out.println("Received something...");
                final String json = new String((byte[]) o);
                final ObfuscateResponse response = gson.fromJson(json, ObfuscateResponse.class);
                final File jar = new File("dev.skidfuscator.obfuscator.cloud/client-cloud/src/main/resources/out.jar");
                try {
                    FileUtils.writeByteArrayToFile(jar, response.getData());
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                //System.out.println("[ " + name  + " ] >> Received user greeting " + new String((byte[]) o));
            }
        });
    }

    public void subscribeGreetings(StompSession stompSession, final String name) throws ExecutionException, InterruptedException {
        stompSession.subscribe("/test/output", new StompFrameHandler() {

            public Type getPayloadType(StompHeaders stompHeaders) {
                return byte[].class;
            }

            public void handleFrame(StompHeaders stompHeaders, Object o) {
                System.out.println("[ " + name  + " ] >> Received greeting " + new String((byte[]) o));
            }
        });

        stompSession.subscribe("/user/test/output", new StompFrameHandler() {

            public Type getPayloadType(StompHeaders stompHeaders) {
                return byte[].class;
            }

            public void handleFrame(StompHeaders stompHeaders, Object o) {
                System.out.println("[ " + name  + " ] >> Received user greeting " + new String((byte[]) o));
            }
        });
    }

    public void sendHello(StompSession stompSession) {
        String jsonHello = gson.toJson(new TestRequest("EEE"), TestRequest.class);
        stompSession.send("/test", jsonHello.getBytes());
    }

    public void sendThing(StompSession stompSession, SkidSessionData data) {
        String jsonHello = gson.toJson(
                new AuthenticateRequest("sk1d123456789!_vaziak_bad_E", data),
                AuthenticateRequest.class
        );
        System.out.println("Attempting to send...");
        stompSession.send("/skid", jsonHello.getBytes());
    }

    public void onMessage(String s) {
        System.out.println(s);
    }
    public void onClose(int code, String reason, boolean remote) {
        System.out.println(
                "Connection closed by " + (remote ? "remote peer" : "us")
                + " Code: " + code
                + " Reason: " + reason
        );
    }

}
