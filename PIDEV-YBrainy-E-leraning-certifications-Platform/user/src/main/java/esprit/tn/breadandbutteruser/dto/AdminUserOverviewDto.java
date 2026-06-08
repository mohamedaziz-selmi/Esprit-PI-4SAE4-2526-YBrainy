package esprit.tn.breadandbutteruser.dto;

import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserOverviewDto {
    private Long userId;
    private String keycloakUserId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String profilePicture;
    private String companyName;
    private String city;
    private String country;
    private IntegrityStatus status;
    private boolean adminVerified;
    private boolean enterpriseVerified;
    private boolean verificationRequired;
    private boolean verificationApproved;
    private boolean banned;
    private String reasonForBan;
    private Integer banPeriod;
    private LocalDateTime lockedUntil;
    private int streakDays;
    private LocalDateTime lastLogin;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastProfileUpdate;
    private long warningCount;
    private long activityCountLast30Days;
    private LocalDateTime latestActivityAt;
}
