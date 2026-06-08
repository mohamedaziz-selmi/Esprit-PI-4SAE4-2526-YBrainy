package com.esprit.threadservice.dto;

import com.esprit.threadservice.model.VoteType;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VoteRequest {
    private VoteType voteType;
}
