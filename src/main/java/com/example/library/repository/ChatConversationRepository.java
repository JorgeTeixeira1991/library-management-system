package com.example.library.repository;

import com.example.library.domain.ChatConversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByConversationKeyAndOwnerUserUsername(String conversationKey, String username);
    boolean existsByConversationKey(String conversationKey);
}
