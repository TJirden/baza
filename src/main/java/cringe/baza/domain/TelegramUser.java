package cringe.baza.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "telegram_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TelegramUser {
    @Id
    private Long id; // Telegram User ID

    private String username;
    private String firstName;

    private Integer battleWins = 0;
    private Integer points = 0;

    @ManyToMany(mappedBy = "members")
    private Set<MemeGroup> groups = new HashSet<>();
}
