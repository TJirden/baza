package cringe.baza.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_moderation")
@Getter
@Setter
@NoArgsConstructor
public class MemeModeration {

    @Id
    private String id;

    private String fileId;

    @Column(length = 2000)
    private String description;

    private Long ownerId;
    private String visibility;
    private String groupIds;

    private String status;

    @Column(length = 1000)
    private String moderationReason;

    private LocalDateTime createdAt = LocalDateTime.now();

    public MemeModeration(
            String id,
            String fileId,
            String description,
            Long ownerId,
            String visibility,
            String groupIds,
            String status,
            String moderationReason) {
        this.id = id;
        this.fileId = fileId;
        this.description = description;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.groupIds = groupIds;
        this.status = status;
        this.moderationReason = moderationReason;
        this.createdAt = LocalDateTime.now();
    }
}
