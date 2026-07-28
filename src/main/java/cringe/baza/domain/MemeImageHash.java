package cringe.baza.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// TODO: migrate to Liquibase (see separate PR)
@Entity
@Table(name = "meme_image_hashes")
@Getter
@Setter
@NoArgsConstructor
public class MemeImageHash {

    @Id
    private String memeId;

    @Column(nullable = false)
    private long imageHash;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public MemeImageHash(String memeId, long imageHash) {
        this.memeId = memeId;
        this.imageHash = imageHash;
    }
}
