package com.goodskill.ai.controller;

import com.goodskill.ai.service.UserSupportAssistant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


/**
 * @author techa03
 */
@RequestMapping("/assistant")
@RestController
@Profile("!production")
@Validated
public class AssistantController {

	private final UserSupportAssistant agent;

	public AssistantController(UserSupportAssistant agent) {
		this.agent = agent;
	}

	@GetMapping(path="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chat(@RequestParam String chatId, @RequestParam String userMessage) {
		return agent.chat(chatId, userMessage);
	}

	@PostMapping(path="/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chat(@Valid @RequestBody ChatRequest request) {
		return agent.chat(request.chatId(), request.message());
	}

	public record ChatRequest(
			@NotBlank @Size(max = 100) String chatId,
			@NotBlank @Size(max = 4000) String message) {
	}

}
