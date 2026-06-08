package esprit.tn.breadandbutteruser.dto.forum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentActivityDto {
    private long commentCount;
    private List<WeekBucketDto> weeklyBuckets = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeekBucketDto {
        private String weekLabel;
        private long count;
    }
}
