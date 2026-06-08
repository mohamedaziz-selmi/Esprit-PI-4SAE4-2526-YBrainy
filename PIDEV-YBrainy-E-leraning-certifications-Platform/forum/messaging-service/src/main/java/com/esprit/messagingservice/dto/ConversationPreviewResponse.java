package com.esprit.messagingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConversationPreviewResponse {
    private Long userId;
    private String username;
    private String role;
    private int level;
    private String levelTitle;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private boolean sentByMe;
}
