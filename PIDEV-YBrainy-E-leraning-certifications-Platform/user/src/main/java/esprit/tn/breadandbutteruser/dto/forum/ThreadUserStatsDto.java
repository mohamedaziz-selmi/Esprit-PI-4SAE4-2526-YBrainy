package esprit.tn.breadandbutteruser.dto.forum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThreadUserStatsDto {
    private long threadCount;
    private long upvotesReceived;
    private long downvotesReceived;
    private long likesReceived;
    private long dislikesReceived;
    private long savesReceived;
    private java.util.List<WeekBucketDto> weeklyBuckets = new java.util.ArrayList<>();

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeekBucketDto {
        private String weekLabel;
        private long count;
    }
}
