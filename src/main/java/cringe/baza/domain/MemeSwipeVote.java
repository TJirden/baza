package cringe.baza.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_swipe_votes")
@Getter
@Setter
@NoArgsConstructor
public class MemeSwipeVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String memeId;
    private Long userId;
    private String voteType; // "BASE" or "CRINGE"
    private LocalDateTime createdAt;

    public MemeSwipeVote(String memeId, Long userId, String voteType) {
        this.memeId = memeId;
        this.userId = userId;
        this.voteType = voteType;
        this.createdAt = LocalDateTime.now();
    }
}
