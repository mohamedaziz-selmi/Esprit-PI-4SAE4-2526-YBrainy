package esprit.tn.breadandbutteruser.dto;

import esprit.tn.breadandbutteruser.entities.XpEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpEventDto {
    private Long id;
    private Long userId;
    private String sourceType;
    private int amount;
    private long newTotal;
    private int newLevel;
    private boolean levelUp;
    private String description;
    private LocalDateTime createdAt;

    public static XpEventDto fromEntity(XpEvent event) {
        if (event == null) return null;
        return XpEventDto.builder()
                .id(event.getId())
                .userId(event.getUser() != null ? event.getUser().getUserId() : null)
                .sourceType(event.getSourceType() != null ? event.getSourceType().name() : null)
                .amount(event.getAmount())
                .newTotal(event.getNewTotal())
                .newLevel(event.getNewLevel())
                .levelUp(event.isLevelUp())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
