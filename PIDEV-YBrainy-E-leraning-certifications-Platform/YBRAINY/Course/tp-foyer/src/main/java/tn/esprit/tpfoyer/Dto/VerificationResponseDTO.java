package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponseDTO {
    private boolean valid;
    private String certificateId;
    private String studentName;
    private Long studentId;
    private String courseTitle;
    private String completionDate;
    private Double quizScore;
    private Integer hoursSpent;
    private String issuedBy;
}
