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
public class BanAppealResponseDto {
    private Long appealId;
    private String description;
    private String appealStatus;
    private LocalDateTime submittedDate;
    private LocalDateTime resolvedDate;
    private String reviewedBy;
    private boolean viewed;
    private LocalDateTime viewedAt;
    private String viewedBy;
    private Long userId;
}
