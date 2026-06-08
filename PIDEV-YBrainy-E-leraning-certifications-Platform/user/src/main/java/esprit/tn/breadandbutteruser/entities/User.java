package esprit.tn.breadandbutteruser.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String username;

    private String firstName;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
    private String keycloakUserId;

    @Convert(converter = RoleAttributeConverter.class)
    @Column(nullable = false)
    private Role role;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Lob
    @Column(name = "profile_picture", columnDefinition = "LONGTEXT")
    private String profilePicture;

    private Integer age;

    private String address;

    private String city;

    private String country;

    private String sex;

    private String companyName;
    @Builder.Default
    private boolean adminVerified = false;
    private LocalDateTime adminVerifiedAt;
    private String adminVerifiedBy;
    @Builder.Default
    private boolean enterpriseVerified = false;
    private LocalDateTime enterpriseOnboardedAt;

    private String faceBiometricHash;
    @Builder.Default
    private int streakDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private long xp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    private LocalDateTime lastLogin;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastProfileUpdate;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "reason_for_ban")
    private String reasonForBan;
    @Column(name = "ban_period")
    private Integer banPeriod;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IntegrityStatus status = IntegrityStatus.SECURE;

    @Column(length = 500)
    private String bio;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "personality_id", referencedColumnName = "personalityId")
    private Personality personality;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    @ToString.Exclude
    private List<Warning> warnings = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    @ToString.Exclude
    private List<BanAppeal> banAppeals = new ArrayList<>();

    public void addWarning(Warning warning) {
        warnings.add(warning);
        warning.setUser(this);
    }

    public void removeWarning(Warning warning) {
        warnings.remove(warning);
        warning.setUser(null);
    }

    public void addBanAppeal(BanAppeal banAppeal) {
        banAppeals.add(banAppeal);
        banAppeal.setUser(this);
    }

    public void removeBanAppeal(BanAppeal banAppeal) {
        banAppeals.remove(banAppeal);
        banAppeal.setUser(null);
    }

    public boolean authenticate() {
        System.out.println("Authenticating user: " + username);
        return true;
    }

    public void updateLevel() {
        System.out.println("Updating user level for: " + username);
    }
}
