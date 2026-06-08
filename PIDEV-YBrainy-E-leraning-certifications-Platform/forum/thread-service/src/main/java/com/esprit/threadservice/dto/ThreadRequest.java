package com.esprit.threadservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ThreadRequest {
    private String title;
    private String body;
    private Long authorId;
    private Long categoryId;
    private String mediaUrl;
    private String mediaType;
}
