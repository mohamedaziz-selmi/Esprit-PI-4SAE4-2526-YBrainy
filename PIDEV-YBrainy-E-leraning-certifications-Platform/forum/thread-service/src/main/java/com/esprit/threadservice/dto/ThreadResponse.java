package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadResponse {
    private Long id;
    private String title;
    private String body;
    private Long authorId;
    private AuthorDto author;
    private Long categoryId;
    private CategoryDto category;
    private String status;
    // Media fields — backward-compatible with Angular's imageUrl/fileUrl/fileType
    private String mediaUrl;
    private String mediaType;
    private String imageUrl;   // set when mediaType == IMAGE
    private String fileUrl;    // set when mediaType == FILE
    private String fileType;   // raw MIME-type or mediaType string
    private long upvoteCount;
    private long downvoteCount;
    private long voteScore;
    private long likeCount;
    private long dislikeCount;
    private boolean savedByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // AI quality fields
    private boolean aiAnalyzed;
    private Integer aiOverallScore;
    private String aiLabel;
    private String aiLabelColor;
    private String aiSummary;
}
