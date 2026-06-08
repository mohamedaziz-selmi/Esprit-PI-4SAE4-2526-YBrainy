package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSearchResultDTO {
    private String explanation;
    private String extractedKeywords;
    private String detectedCategory;
    private String detectedLevel;
    private Page<CourseResponseDTO> results;
}
