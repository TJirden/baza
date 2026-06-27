package cringe.baza.domain;

import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Column(length = 2000)
    private String ocrText;

    private Long ownerId;

    @Enumerated(EnumType.STRING)
    private MemeVisibility visibility;

    private String groupIds;

    @Enumerated(EnumType.STRING)
    private ModerationStatus status;

    @Column(length = 1000)
    private String moderationReason;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "meme", cascade = CascadeType.ALL, orphanRemoval = true)
    private MemeRating rating;

    @OneToMany(mappedBy = "meme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemeReport> reports = new ArrayList<>();

    @OneToMany(mappedBy = "meme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemeSwipeVote> swipeVotes = new ArrayList<>();

    @OneToMany(mappedBy = "memeA", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemeBattle> battlesAsA = new ArrayList<>();

    @OneToMany(mappedBy = "memeB", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemeBattle> battlesAsB = new ArrayList<>();

    public MemeModeration(
            String id,
            String fileId,
            String description,
            String ocrText,
            Long ownerId,
            MemeVisibility visibility,
            String groupIds,
            ModerationStatus status,
            String moderationReason) {
        this.id = id;
        this.fileId = fileId;
        this.description = description;
        this.ocrText = ocrText;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.groupIds = groupIds;
        this.status = status;
        this.moderationReason = moderationReason;
        this.createdAt = LocalDateTime.now();
    }
}
