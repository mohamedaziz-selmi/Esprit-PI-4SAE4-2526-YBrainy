package esprit.tn.breadandbutteruser.dto;

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
    @NotBlank(message = "Reason is required")
    private String reason;

    @NotBlank(message = "Severity is required")
    private String severity;

    @NotNull(message = "userId is required")
    private Long userId;
}
