package dev.skidfuscator.backend.api;

import dev.skidfuscator.backend.service.ObfuscatorService;
import dev.skidfuscator.protocol.request.AuthenticateRequest;
import dev.skidfuscator.protocol.request.DataRequest;
import dev.skidfuscator.protocol.request.ExecuteRequest;
import dev.skidfuscator.protocol.request.TestRequest;
import dev.skidfuscator.protocol.response.ObfuscateResponse;
import dev.skidfuscator.protocol.response.TextResponse;
import dev.skidfuscator.backend.session.ObfuscatorUserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ApiController {
    public ApiController() {
    }
    @Autowired
    private SimpMessagingTemplate stompClient;

    @Autowired
    private ObfuscatorService obfuscatorService;

    @MessageMapping("/test")
    @SendToUser("/test/output")
    public TextResponse test(TestRequest message, Principal principal) {
        System.out.println("Connected from " + message.getCode());
        stompClient.convertAndSendToUser(
                principal.getName(), "/test/output", TextResponse.success("fucking retard " + principal.getName()));
        return TextResponse.success(principal.getName());
    }

    @MessageMapping("/skid/init")
    @SendToUser("/skid/logs") // use @SendToUser instead of @SendTo
    public TextResponse handleInit(AuthenticateRequest message, Principal principal) {
        if (!message.getCode().equals("sk1d123456789!_vaziak_bad_E")) {
            return TextResponse.error("Failed to authenticate appropriately...");
        }

        final ObfuscatorUserSession user = (ObfuscatorUserSession) principal;
        System.out.println("Creating a session of id " + user.getUuid());

        obfuscatorService.create(user, message.getSession());

        return TextResponse.success("Successfully connected!");
    }

    @MessageMapping("/skid/jar")
    @SendToUser("/skid/logs") // use @SendToUser instead of @SendTo
    public TextResponse handleJar(DataRequest message, Principal principal) {
        final ObfuscatorUserSession user = (ObfuscatorUserSession) principal;
        System.out.println("Handling jar of session of id " + user.getUuid());

        obfuscatorService.addFile(user, message.getData().getDocument());

        return TextResponse.success("Successfully connected!");
    }

    @MessageMapping("/skid/mappings")
    @SendToUser("/skid/logs") // use @SendToUser instead of @SendTo
    public TextResponse handleMappings(DataRequest message, Principal principal) {
        final ObfuscatorUserSession user = (ObfuscatorUserSession) principal;

        if (message.isExecute()) {
            System.out.println("Handling execution of session of id " + user.getUuid());

            try {
                obfuscatorService.execute(user);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Received execute command....");

            return TextResponse.success("Successfully connected!");
        }

        System.out.println("Handling mappings of session of id " + user.getUuid());
        System.out.println("Got " + message.getData().getDocument().length);

        obfuscatorService.addMapping(user, message.getData().getDocument());

        return TextResponse.success("Successfully connected!");
    }
}
