package com.esprit.userservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CommentCreatedEvent {
    private Long commentId;
    private Long authorId;
    private Long postId;
    private Long threadId;
}
