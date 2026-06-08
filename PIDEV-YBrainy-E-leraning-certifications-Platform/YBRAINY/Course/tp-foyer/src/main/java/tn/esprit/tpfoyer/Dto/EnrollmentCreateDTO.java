package tn.esprit.tpfoyer.Dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnrollmentCreateDTO {

    @NotNull
    private Long studentId;

    @NotNull
    private Long courseId;
}
