package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSearchIntentDTO {
    private String keywords;
    private String category;
    private String level;
    private String explanation;
}
