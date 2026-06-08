package tn.esprit.warningbanappealservice.dto;

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
    private String warningId;
    private Long userId;
    private String reason;
    private String severity;
    private String issuedBy;
    private LocalDateTime issuedDate;
}
