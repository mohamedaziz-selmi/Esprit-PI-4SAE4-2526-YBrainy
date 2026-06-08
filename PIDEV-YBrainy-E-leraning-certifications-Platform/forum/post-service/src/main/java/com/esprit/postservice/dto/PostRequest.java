package com.esprit.postservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PostRequest {
    private String body;       // Angular sends "body"
    private Long authorId;
    private Long threadId;
    private String mediaUrl;
    private String mediaType;
}
