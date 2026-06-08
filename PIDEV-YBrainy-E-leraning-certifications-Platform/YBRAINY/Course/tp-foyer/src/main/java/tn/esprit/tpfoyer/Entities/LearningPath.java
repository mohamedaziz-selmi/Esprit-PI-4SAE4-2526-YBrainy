package tn.esprit.tpfoyer.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_paths")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;

    @Column(length = 500)
    private String goal;

    private String generatedTitle;

    @Column(length = 1000)
    private String generatedDescription;

    @Column(length = 1000)
    private String courseIds;

    @Column(name = "why_included", columnDefinition = "TEXT")
    private String whyIncluded;

    private BigDecimal totalPrice;

    private Integer totalDurationMinutes;

    @Builder.Default
    private Boolean saved = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
