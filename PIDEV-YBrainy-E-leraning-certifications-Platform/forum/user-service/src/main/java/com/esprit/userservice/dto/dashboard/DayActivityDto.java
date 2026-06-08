package com.esprit.userservice.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DayActivityDto {
    private String weekLabel;
    private long threadsCount;
    private long postsCount;
    private long commentsCount;
}
