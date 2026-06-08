package com.esprit.messagingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserSummaryResponse {
    private Long id;
    private String username;
    private String role;
    private int level;
    private String levelTitle;
}
