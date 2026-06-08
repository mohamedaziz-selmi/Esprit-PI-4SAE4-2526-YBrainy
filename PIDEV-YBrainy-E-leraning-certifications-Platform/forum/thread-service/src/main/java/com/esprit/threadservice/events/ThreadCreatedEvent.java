package com.esprit.threadservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ThreadCreatedEvent {
    private Long threadId;
    private Long authorId;
    private String title;
}
