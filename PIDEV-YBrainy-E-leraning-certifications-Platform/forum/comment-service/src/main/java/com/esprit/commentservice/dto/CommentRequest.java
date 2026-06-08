package com.esprit.commentservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CommentRequest {
    private String body;       // Angular sends "body"
    private Long authorId;
    private Long postId;
    private Long threadId;
}
