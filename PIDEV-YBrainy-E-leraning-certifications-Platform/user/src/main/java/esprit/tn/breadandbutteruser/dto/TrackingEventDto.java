package esprit.tn.breadandbutteruser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingEventDto {
    @NotBlank
    private String eventType;

    @NotNull
    private Long occurredAtEpochMs;

    private String route;

    private Long durationMs;

    private Map<String, Object> metadata;
}
