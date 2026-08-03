package com.example.library.web;

import com.example.library.dto.ChatDtos.ChatRequest;
import com.example.library.dto.ChatDtos.ChatResponse;
import com.example.library.service.ChatAssistantService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AIController {

    private final ChatAssistantService chatAssistantService;

    public AIController(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Principal principal) {
        return chatAssistantService.chat(principal.getName(), request.conversationId(), request.message());
    }
}
