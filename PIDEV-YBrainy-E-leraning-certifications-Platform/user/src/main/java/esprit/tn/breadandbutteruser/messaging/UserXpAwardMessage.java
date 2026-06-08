package esprit.tn.breadandbutteruser.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserXpAwardMessage {
    private Long userId;
    private Integer amount;
    private String sourceType;
    private String description;
}
