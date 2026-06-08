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
public class MessageResponse {
    private Long id;
    private Long senderId;
    private String senderUsername;
    private String senderRole;
    private int senderLevel;
    private String senderLevelTitle;
    private Long receiverId;
    private String receiverUsername;
    private String content;
    private String mediaUrl;
    private String mediaType;
    private boolean read;
    private LocalDateTime createdAt;
}
