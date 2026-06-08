package com.esprit.messagingservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDto {
    /**
     * breadandbutteruser serialises the PK as "userId".
     * @JsonProperty makes Jackson use "userId" as the primary JSON key for this field.
     */
    @JsonProperty("userId")
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    @JsonAlias("avatarUrl")
    private String profilePicture;
    private String companyName;
    private String bio;
    private long xp;
    private int level;
    private String levelTitle;
    private int streakDays;
    private String status;

    /** Convenience: returns the user ID regardless of which field Jackson populated. */
    public Long getEffectiveId() {
        return id;
    }
}
