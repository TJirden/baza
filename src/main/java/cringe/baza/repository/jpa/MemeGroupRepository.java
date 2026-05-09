package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemeGroupRepository extends JpaRepository<MemeGroup, Long> {
    List<MemeGroup> findByMembersContains(TelegramUser member);
    List<MemeGroup> findByOwner(TelegramUser owner);
}
