package tn.esprit.warningbanappealservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanAppealRequestDto {
    @NotNull
    private Long userId;
    @NotBlank
    private String description;
    private String appealStatus;
    private Boolean viewed;
    private String reviewedBy;
}
