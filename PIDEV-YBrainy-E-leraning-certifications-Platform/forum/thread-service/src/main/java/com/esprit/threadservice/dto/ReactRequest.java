package com.esprit.threadservice.dto;

import com.esprit.threadservice.model.ReactionType;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReactRequest {
    private ReactionType reactionType;
}
