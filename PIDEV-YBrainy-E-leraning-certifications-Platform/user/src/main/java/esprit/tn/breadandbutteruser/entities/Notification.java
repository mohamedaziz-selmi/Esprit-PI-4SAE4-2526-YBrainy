package esprit.tn.breadandbutteruser.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_notifications")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    private Long threadId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
