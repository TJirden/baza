package cringe.baza.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "meme_battles")
@Getter
@Setter
@NoArgsConstructor
public class MemeBattle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meme_aid")
    private String memeAId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_aid", referencedColumnName = "id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private MemeModeration memeA;

    @Column(name = "meme_bid")
    private String memeBId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_bid", referencedColumnName = "id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private MemeModeration memeB;

    private Integer votesA = 0;
    private Integer votesB = 0;

    private String winnerMemeId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String status;

    private Long telegramChatId;
    private Integer telegramMessageId;

    private String battleType = "AUTO";
    private Long challengerId;
    private Long opponentId;
    private Integer bet = 0;
    private Boolean challengerMemeSelected = false;
    private Boolean opponentMemeSelected = false;

    @OneToMany(mappedBy = "battle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemeBattleVote> votes = new ArrayList<>();
}
