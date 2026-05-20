package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeGroupRepository extends JpaRepository<MemeGroup, Long> {
  List<MemeGroup> findByMembersContains(TelegramUser member);

  List<MemeGroup> findByOwner(TelegramUser owner);

  List<MemeGroup> findByNameContainingIgnoreCase(String name);
}
