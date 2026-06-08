package esprit.tn.breadandbutteruser.dto.forum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlQualityStatsDto {
    private double hqRate;
    private long hqCount;
    private long lqEditCount;
    private long lqCloseCount;
    private long totalAnalyzed;
}
