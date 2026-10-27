package com.example.cart_rush.controller;

import com.example.cart_rush.service.assistant.AssistantChatRequest;
import com.example.cart_rush.service.assistant.AssistantChatResponse;
import com.example.cart_rush.service.assistant.AssistantStreamEvent;
import com.example.cart_rush.service.assistant.ShoppingAssistantService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final ShoppingAssistantService assistantService;
    private final Executor assistantStreamExecutor;

    public AssistantController(ShoppingAssistantService assistantService,
                               @Qualifier("assistantStreamExecutor") Executor assistantStreamExecutor) {
        this.assistantService = assistantService;
        this.assistantStreamExecutor = assistantStreamExecutor;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request.message());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AssistantChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        assistantStreamExecutor.execute(() -> {
            try {
                assistantService.streamChat(request, event -> {
                    try {
                        emitter.send(SseEmitter.event().name(event.type()).data(event));
                    } catch (IOException exception) {
                        throw new ClientDisconnectedException();
                    }
                });
                emitter.complete();
            } catch (ClientDisconnectedException ignored) {
                emitter.complete();
            } catch (RuntimeException exception) {
                log.warn("Assistant stream ended unexpectedly ({})", exception.getClass().getSimpleName());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(AssistantStreamEvent.error("The assistant stream could not complete. Please try again.", null)));
                } catch (IOException ignored) {
                    // Client has already disconnected.
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    private static class ClientDisconnectedException extends RuntimeException {
    }
}