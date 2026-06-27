package cringe.baza.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_ratings")
@Getter
@Setter
@NoArgsConstructor
public class MemeRating {
    @Id
    private String memeId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne
    @MapsId
    @JoinColumn(name = "meme_id")
    private MemeModeration meme;

    private Integer eloRating = 1000;
    private Integer wins = 0;
    private Integer losses = 0;
    private LocalDateTime lastBattleTime;

    public MemeRating(String memeId, Integer eloRating, Integer wins, Integer losses, LocalDateTime lastBattleTime) {
        this.memeId = memeId;
        this.eloRating = eloRating;
        this.wins = wins;
        this.losses = losses;
        this.lastBattleTime = lastBattleTime;
    }
}
