package cringe.baza.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemeRating {
    @Id
    private String memeId;

    private Integer eloRating = 1000;
    private Integer wins = 0;
    private Integer losses = 0;
    private LocalDateTime lastBattleTime;
}
