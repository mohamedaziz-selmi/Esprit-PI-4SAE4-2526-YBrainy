package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDetailsDTO {
    private String certificateId;
    private String studentName;
    private Long studentId;
    private String courseTitle;
    private String courseCategory;
    private String completionDate;
    private Double quizScore;
    private Integer hoursSpent;
    private String aiRemarks;
    @Builder.Default
    private String issuedBy = "YBrainy E-Learning Platform";
}
