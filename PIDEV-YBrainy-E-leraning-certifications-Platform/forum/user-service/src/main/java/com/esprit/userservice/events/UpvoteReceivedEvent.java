package com.esprit.userservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UpvoteReceivedEvent {
    private Long threadId;
    private Long threadAuthorId;
    private Long voterId;
}
