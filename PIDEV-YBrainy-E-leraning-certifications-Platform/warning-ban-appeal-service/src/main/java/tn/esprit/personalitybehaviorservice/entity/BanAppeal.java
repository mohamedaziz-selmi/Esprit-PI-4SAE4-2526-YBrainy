package tn.esprit.warningbanappealservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "ban_appeals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanAppeal {
    @Id
    private String appealId;
    @Indexed
    private Long userId;
    private String description;
    private String appealStatus;
    private LocalDateTime submittedDate;
    private LocalDateTime resolvedDate;
    private String reviewedBy;
    @Builder.Default
    private boolean viewed = false;
    private LocalDateTime viewedAt;
    private String viewedBy;
}
