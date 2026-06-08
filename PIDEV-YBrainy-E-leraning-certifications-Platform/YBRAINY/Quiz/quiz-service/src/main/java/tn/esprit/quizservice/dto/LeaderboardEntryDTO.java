package tn.esprit.quizservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDTO {
    private int rank;
    private Long studentId;
    private String studentName;
    private Double score;
    private String rankTitle;
    private LocalDateTime attemptedAt;
}
