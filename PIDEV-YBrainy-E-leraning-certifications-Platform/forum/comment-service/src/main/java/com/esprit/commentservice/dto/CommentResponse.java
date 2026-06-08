package com.esprit.commentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CommentResponse {
    private Long id;
    private String body;       // Angular expects "body"
    private Long authorId;
    private AuthorDto author;
    private Long postId;
    private Long threadId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
