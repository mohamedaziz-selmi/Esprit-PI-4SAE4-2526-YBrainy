package tn.esprit.tpfoyer.Events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDeletedEvent {
    private Long courseId;
    private String courseTitle;
}
