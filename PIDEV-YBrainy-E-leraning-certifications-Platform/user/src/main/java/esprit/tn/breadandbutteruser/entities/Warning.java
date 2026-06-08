package esprit.tn.breadandbutteruser.entities;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "warnings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warningId;

    private String reason;
    private String severity;
    private LocalDateTime issuedDate;
    private String issuedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    @ToString.Exclude
    private User user;

    // Business methods
    public void recordWarning() {
        System.out.println("Recording warning: " + reason + " with severity: " + severity);
    }

    public void escalate() {
        System.out.println("Escalating warning severity...");
    }
}