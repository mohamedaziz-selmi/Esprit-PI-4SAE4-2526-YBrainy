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
public class WarningRequestDto {
    @NotNull
    private Long userId;
    @NotBlank
    private String reason;
    @NotBlank
    private String severity;
    @NotBlank
    private String issuedBy;
}
