package cringe.baza.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashSet;
import java.util.Set;

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

    @ManyToMany(mappedBy = "members")
    private Set<MemeGroup> groups = new HashSet<>();
}
