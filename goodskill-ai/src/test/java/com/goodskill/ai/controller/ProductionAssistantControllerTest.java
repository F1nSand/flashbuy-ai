package com.goodskill.ai.controller;

import com.goodskill.ai.service.UserSupportAssistant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionAssistantControllerTest {

    @Test
    void scopesConversationMemoryToAuthenticatedUser() {
        UserSupportAssistant assistant = mock(UserSupportAssistant.class);
        when(assistant.chat("buyer-42:session-1", "Where is my order?"))
                .thenReturn(Flux.just("Please sign in to view order details."));
        ProductionAssistantController controller = new ProductionAssistantController(assistant);

        controller.chat("buyer-42", new AssistantController.ChatRequest("session-1", "Where is my order?"));

        verify(assistant).chat("buyer-42:session-1", "Where is my order?");
    }
}
