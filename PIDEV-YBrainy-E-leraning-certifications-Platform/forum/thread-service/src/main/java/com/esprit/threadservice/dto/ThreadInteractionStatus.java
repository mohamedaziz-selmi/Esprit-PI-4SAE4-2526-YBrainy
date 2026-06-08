package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ThreadInteractionStatus {
    private Long threadId;
    private Long userId;
    private String userVote;        // Angular: userVote
    private String userReaction;    // Angular: userReaction
    private boolean wishlisted;     // Angular: wishlisted
    private long upvoteCount;       // Angular: upvoteCount
    private long downvoteCount;     // Angular: downvoteCount
    private long voteScore;         // Angular: voteScore
    private long likeCount;         // Angular: likeCount
    private long dislikeCount;      // Angular: dislikeCount
}
