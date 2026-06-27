package cringe.baza.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    @Column(name = "meme_id")
    private String memeId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_id", referencedColumnName = "id", insertable = false, updatable = false)
    private MemeModeration meme;

    private Long reporterUserId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public MemeReport(String memeId, Long reporterUserId) {
        this.memeId = memeId;
        this.reporterUserId = reporterUserId;
        this.createdAt = LocalDateTime.now();
    }
}
