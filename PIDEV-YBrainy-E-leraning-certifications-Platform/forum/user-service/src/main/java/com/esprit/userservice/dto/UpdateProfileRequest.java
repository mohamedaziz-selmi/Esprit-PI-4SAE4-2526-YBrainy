package com.esprit.userservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateProfileRequest {
    private String username;
    private String bio;
    private String avatarUrl;
}
