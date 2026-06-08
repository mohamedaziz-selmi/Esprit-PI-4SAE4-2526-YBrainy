package com.esprit.userservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class BestAnswerEvent {
    private Long postId;
    private Long postAuthorId;
    private Long threadId;
}
