package esprit.tn.breadandbutteruser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarningResponseDto {
    private Long warningId;
    private String reason;
    private String severity;
    private LocalDateTime issuedDate;
    private String issuedBy;
    private Long userId;
}
