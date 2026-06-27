package cringe.baza.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "meme_battle_votes",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"battleId", "userId"})})
@Getter
@Setter
@NoArgsConstructor
public class MemeBattleVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "battle_id")
    private Long battleId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id", referencedColumnName = "id", insertable = false, updatable = false)
    private MemeBattle battle;

    private Long userId;
    private String votedFor; // "A" or "B"
    private LocalDateTime votedAt = LocalDateTime.now();

    public MemeBattleVote(Long battleId, Long userId, String votedFor) {
        this.battleId = battleId;
        this.userId = userId;
        this.votedFor = votedFor;
        this.votedAt = LocalDateTime.now();
    }
}
