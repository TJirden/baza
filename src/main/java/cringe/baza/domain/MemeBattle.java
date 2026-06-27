package cringe.baza.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meme_battles")
@Getter
@Setter
@NoArgsConstructor
public class MemeBattle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String memeAId;
    private String memeBId;

    private Integer votesA = 0;
    private Integer votesB = 0;

    private String winnerMemeId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String status; // "ACTIVE", "COMPLETED", "PENDING", "MEME_SELECTION", "DECLINED", "EXPIRED"

    private Long telegramChatId;
    private Integer telegramMessageId;

    private String battleType = "AUTO"; // "AUTO", "DUEL"
    private Long challengerId;
    private Long opponentId;
    private Integer bet = 0;
    private Boolean challengerMemeSelected = false;
    private Boolean opponentMemeSelected = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "battleId")
    private List<MemeBattleVote> votes = new ArrayList<>();
}
