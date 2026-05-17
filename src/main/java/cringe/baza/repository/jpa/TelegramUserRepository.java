package cringe.baza.repository.jpa;

import cringe.baza.domain.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    List<TelegramUser> findByUsernameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(String username, String firstName);
}
