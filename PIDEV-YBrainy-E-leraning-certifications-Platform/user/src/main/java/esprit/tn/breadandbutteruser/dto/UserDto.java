package esprit.tn.breadandbutteruser.dto;

import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private LocalDate dateOfBirth;
    private String profilePicture;
    private Integer age;
    private String address;
    private String city;
    private String country;
    private String sex;
    private String companyName;
    private boolean adminVerified;
    private LocalDateTime adminVerifiedAt;
    private String adminVerifiedBy;
    private boolean enterpriseVerified;
    private LocalDateTime enterpriseOnboardedAt;
    private boolean faceBiometricRegistered;
    private int streakDays;
    private long xp;
    private int level;
    private LocalDateTime lastActiveAt;
    private String bio;
    private LocalDateTime lastLogin;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastProfileUpdate;
    private LocalDateTime lockedUntil;
    private String reasonForBan;
    private Integer banPeriod;
    private IntegrityStatus status;
}
