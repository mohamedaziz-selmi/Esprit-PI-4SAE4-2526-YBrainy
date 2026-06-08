package com.esprit.postservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PostResponse {
    private Long id;
    private String body;
    private Long authorId;
    private AuthorDto author;
    private Long threadId;
    private String threadTitle;   // fetched via Feign
    private String mediaUrl;
    private String mediaType;
    private String imageUrl;      // mapped from mediaUrl when mediaType == IMAGE
    private String fileUrl;       // mapped from mediaUrl when mediaType == FILE
    private String fileType;      // raw mediaType string
    private boolean bestAnswer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
