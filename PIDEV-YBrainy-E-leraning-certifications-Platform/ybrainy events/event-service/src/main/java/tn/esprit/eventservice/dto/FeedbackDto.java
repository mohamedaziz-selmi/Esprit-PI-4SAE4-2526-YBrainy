package tn.esprit.eventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDto {
    private long idFeedback;
    private long studentId;
    private long eventId;
    private int rating;
    private String comment;
    private String sentimentLabel;
    private LocalDateTime dateCreation;
    private String statut;
}
