package com.goodskill.ai.controller;

import com.goodskill.ai.service.UserSupportAssistant;
import com.goodskill.core.enums.CommonConstants;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Authenticated, read-only AI endpoint for production. The Gateway overwrites the actor header
 * after token validation; including it in the memory key isolates conversations between users.
 */
@RestController
@RequestMapping("/assistant")
@Profile("production")
@Validated
public class ProductionAssistantController {

    private final UserSupportAssistant assistant;

    public ProductionAssistantController(UserSupportAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestHeader(CommonConstants.USER_ID_HEADER) String authenticatedUserId,
            @Valid @RequestBody AssistantController.ChatRequest request) {
        String conversationId = authenticatedUserId + ":" + request.chatId();
        return assistant.chat(conversationId, request.message());
    }
}
