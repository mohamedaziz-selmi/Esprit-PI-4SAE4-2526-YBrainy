package com.esprit.userservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class PostCreatedEvent {
    private Long postId;
    private Long authorId;
    private Long threadId;
}
