package cringe.baza.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
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

    private String status; // "ACTIVE", "COMPLETED"

    private Long telegramChatId;
    private Integer telegramMessageId;
}
