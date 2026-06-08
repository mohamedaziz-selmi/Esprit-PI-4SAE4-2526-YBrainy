package tn.esprit.tpfoyer.Dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CourseDetailResponseDTO extends CourseResponseDTO {
    private List<LessonResponseDTO> lessons;
}