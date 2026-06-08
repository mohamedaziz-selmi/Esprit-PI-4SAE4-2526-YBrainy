package com.esprit.postservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorDto {
    @JsonAlias("userId")
    private Long id;
    @JsonAlias("id")
    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    @JsonAlias("profilePicture")
    private String avatarUrl;
    @JsonAlias("avatarUrl")
    private String profilePicture;
    private String companyName;
    private String bio;
    private long xp;
    private int level;
    private String levelTitle;
    private int streakDays;
    private String status;
}
