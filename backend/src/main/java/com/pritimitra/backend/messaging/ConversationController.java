package com.pritimitra.backend.messaging;

import com.pritimitra.backend.messaging.ConversationDtos.CatchUpPage;
import com.pritimitra.backend.messaging.ConversationDtos.ConversationPage;
import com.pritimitra.backend.messaging.ConversationDtos.ConversationSummary;
import com.pritimitra.backend.messaging.ConversationDtos.MessagePage;
import com.pritimitra.backend.messaging.ConversationDtos.MessageSummary;
import com.pritimitra.backend.messaging.ConversationDtos.ReadRequest;
import com.pritimitra.backend.messaging.ConversationDtos.ReadState;
import com.pritimitra.backend.messaging.ConversationDtos.SendMessageRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, participant-only conversations, message history, catch-up, sends, and read state. */
@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController {

	private final ConversationService conversations;

	ConversationController(ConversationService conversations) {
		this.conversations = conversations;
	}

	@GetMapping
	ConversationPage list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return conversations.list(jwt.getSubject(), cursor, size);
	}

	@GetMapping("/{conversationPublicId}")
	ConversationSummary get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationPublicId) {
		return conversations.get(jwt.getSubject(), conversationPublicId);
	}

	@GetMapping("/{conversationPublicId}/messages")
	MessagePage messages(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID conversationPublicId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return conversations.messages(jwt.getSubject(), conversationPublicId, cursor, size);
	}

	@GetMapping("/{conversationPublicId}/messages/newer")
	CatchUpPage newer(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID conversationPublicId,
			@RequestParam(name = "cursor") String cursor,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return conversations.newer(jwt.getSubject(), conversationPublicId, cursor, size);
	}

	@PostMapping("/{conversationPublicId}/messages")
	MessageSummary send(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID conversationPublicId,
			@RequestBody SendMessageRequest request) {
		return conversations.send(jwt.getSubject(), conversationPublicId, request.clientMessageId(), request.body());
	}

	@PutMapping("/{conversationPublicId}/read")
	ReadState markRead(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID conversationPublicId,
			@RequestBody ReadRequest request) {
		return conversations.markRead(jwt.getSubject(), conversationPublicId, request.messageId());
	}
}
