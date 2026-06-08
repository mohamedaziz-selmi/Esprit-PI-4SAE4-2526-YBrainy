package tn.esprit.quizservice.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDeletedEvent {
    private Long courseId;
    private String courseTitle;
}
