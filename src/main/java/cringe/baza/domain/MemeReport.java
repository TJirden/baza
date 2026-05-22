package cringe.baza.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_reports")
@Getter
@Setter
@NoArgsConstructor
public class MemeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String memeId;
    private Long reporterUserId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public MemeReport(String memeId, Long reporterUserId) {
        this.memeId = memeId;
        this.reporterUserId = reporterUserId;
        this.createdAt = LocalDateTime.now();
    }
}
