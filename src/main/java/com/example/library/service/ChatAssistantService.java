package com.example.library.service;

import com.example.library.domain.AppUser;
import com.example.library.domain.ChatConversation;
import com.example.library.domain.ChatMessage;
import com.example.library.domain.ChatSenderType;
import com.example.library.dto.BookDtos.BookResponse;
import com.example.library.dto.ChatDtos.ChatResponse;
import com.example.library.dto.RecommendationDtos.RecommendationResponse;
import com.example.library.exception.ConflictException;
import com.example.library.repository.ChatConversationRepository;
import com.example.library.repository.ChatMessageRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatAssistantService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserService userService;
    private final BookService bookService;
    private final RecommendationService recommendationService;

    public ChatAssistantService(
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            UserService userService,
            BookService bookService,
            RecommendationService recommendationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.bookService = bookService;
        this.recommendationService = recommendationService;
    }

    @Transactional
    @PreAuthorize("#username == authentication.name")
    public ChatResponse chat(String username, String conversationId, String message) {
        ChatConversation conversation = conversationRepository
                .findByConversationKeyAndOwnerUserUsername(conversationId, username)
                .orElseGet(() -> createConversation(username, conversationId));

        saveMessage(conversation, ChatSenderType.USER, message);
        String answer = answer(username, message);
        saveMessage(conversation, ChatSenderType.ASSISTANT, answer);
        return new ChatResponse(conversationId, answer);
    }

    private ChatConversation createConversation(String username, String conversationId) {
        if (conversationRepository.existsByConversationKey(conversationId)) {
            throw new ConflictException("Conversation ID is already owned by another user");
        }
        AppUser user = userService.requireByUsername(username);
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationKey(conversationId);
        conversation.setOwnerUser(user);
        return conversationRepository.save(conversation);
    }

    private void saveMessage(ChatConversation conversation, ChatSenderType sender, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversation(conversation);
        chatMessage.setSenderType(sender);
        chatMessage.setContent(content);
        messageRepository.save(chatMessage);
    }

    private String answer(String username, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("recommend")) {
            List<RecommendationResponse> recommendations = recommendationService.recommend(username);
            if (recommendations.isEmpty()) {
                return "I do not have a recommendation yet. Borrow or return a few books so I can use your history.";
            }
            return recommendations.stream()
                    .map(item -> item.book().title() + " — " + item.reason())
                    .limit(3)
                    .reduce("Recommended books:\n", (result, item) -> result + "- " + item + "\n");
        }
        if (normalized.contains("search") || normalized.contains("find")) {
            String query = extractSearchQuery(message);
            List<BookResponse> books = bookService.search(query);
            if (books.isEmpty()) {
                return "No active books matched: " + query;
            }
            return books.stream()
                    .limit(5)
                    .map(book -> book.title() + " by " + book.authors()
                            + " (available: " + book.availableCopies() + ")")
                    .reduce("Search results:\n", (result, item) -> result + "- " + item + "\n");
        }
        if (normalized.contains("borrow")) {
            return "To borrow a book, call POST /api/v1/books/{bookId}/borrow using your client credentials.";
        }
        if (normalized.contains("return")) {
            return "To return a book, call POST /api/v1/loans/{loanId}/return using your client credentials.";
        }
        return "I can search the catalogue, suggest books, and explain how to borrow, return, or join a waitlist."
                + " This offline demo assistant stores the full conversation in PostgreSQL and does not require an AI API key.";
    }

    private String extractSearchQuery(String message) {
        String query = message
                .replaceFirst("(?i).*?(search|find)(\\s+for)?\\s*", "")
                .trim();
        return query.isBlank() ? message : query;
    }
}
