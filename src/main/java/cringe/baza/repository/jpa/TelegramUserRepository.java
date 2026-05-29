package cringe.baza.repository.jpa;

import cringe.baza.domain.TelegramUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    List<TelegramUser> findByUsernameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
            String username, String firstName);

    List<TelegramUser> findTop5ByOrderByPointsDesc();
}
