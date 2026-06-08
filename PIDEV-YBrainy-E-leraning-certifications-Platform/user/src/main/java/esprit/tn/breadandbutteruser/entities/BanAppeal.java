package esprit.tn.breadandbutteruser.entities;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ban_appeals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long appealId;

    @Column(length = 1000)
    private String description;

    private String appealStatus;
    private LocalDateTime submittedDate;
    private LocalDateTime resolvedDate;
    private String reviewedBy;
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean viewed = false;
    private LocalDateTime viewedAt;
    private String viewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    @ToString.Exclude
    private User user;

    // Business methods
    public void submitAppeal() {
        this.submittedDate = LocalDateTime.now();
        this.appealStatus = "PENDING";
        this.viewed = false;
        this.viewedAt = null;
        this.viewedBy = null;
        System.out.println("Appeal submitted: " + description);
    }

    public void reviewAppeal() {
        System.out.println("Reviewing appeal ID: " + appealId);
    }

    public void approve() {
        this.appealStatus = "APPROVED";
        this.resolvedDate = LocalDateTime.now();
        System.out.println("Appeal approved.");
    }

    public void reject() {
        this.appealStatus = "REJECTED";
        this.resolvedDate = LocalDateTime.now();
        System.out.println("Appeal rejected.");
    }

    public void markViewed(String actor) {
        this.viewed = true;
        this.viewedAt = LocalDateTime.now();
        this.viewedBy = actor;
    }

    public void markUnviewed() {
        this.viewed = false;
        this.viewedAt = null;
        this.viewedBy = null;
    }
}
