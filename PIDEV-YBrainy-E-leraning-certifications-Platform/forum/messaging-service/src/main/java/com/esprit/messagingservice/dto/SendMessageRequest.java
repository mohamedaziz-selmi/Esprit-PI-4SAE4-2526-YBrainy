package com.esprit.messagingservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SendMessageRequest {
    private Long senderId;
    private Long receiverId;
    private String content;
    private String mediaUrl;
    private String mediaType;
}
