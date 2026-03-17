package cringe.baza.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "memes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String filename;

    @Column(length = 1000)
    private String description;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "file_size")
    private Long fileSize;

    private String contentType;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}