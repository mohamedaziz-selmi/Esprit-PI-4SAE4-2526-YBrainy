package tn.esprit.enrollmentservice.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentCompletedEvent {
    private Long enrollmentId;
    private Long studentId;
    private Long courseId;
    private String certificateId;
}
