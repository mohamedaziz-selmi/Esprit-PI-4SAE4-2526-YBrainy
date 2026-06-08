package tn.esprit.warningbanappealservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "warnings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warning {
    @Id
    private String warningId;
    @Indexed
    private Long userId;
    private String reason;
    private String severity;
    private LocalDateTime issuedDate;
    private String issuedBy;
}
