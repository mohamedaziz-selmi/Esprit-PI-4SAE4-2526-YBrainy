package esprit.tn.breadandbutteruser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
