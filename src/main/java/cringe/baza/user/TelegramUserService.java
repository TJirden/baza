package cringe.baza.user;

import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TelegramUserService {

    private final TelegramUserRepository userRepository;
    private final MemeGroupRepository groupRepository;

    @Transactional
    public TelegramUser getOrCreateUser(Long id, String username, String firstName) {
        return userRepository
                .findById(id)
                .map(existingUser -> {
                    boolean updated = false;
                    if (username != null && !username.equals(existingUser.getUsername())) {
                        existingUser.setUsername(username);
                        updated = true;
                    }
                    if (firstName != null && !firstName.equals(existingUser.getFirstName())) {
                        existingUser.setFirstName(firstName);
                        updated = true;
                    }
                    if (updated) {
                        return userRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    TelegramUser newUser = new TelegramUser();
                    newUser.setId(id);
                    newUser.setUsername(username);
                    newUser.setFirstName(firstName);
                    return userRepository.save(newUser);
                });
    }

    @Transactional(readOnly = true)
    public List<Long> getUserGroupIds(Long userId) {
        Optional<TelegramUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return List.of();
        }
        return groupRepository.findByMembersContains(userOpt.get()).stream()
                .map(MemeGroup::getId)
                .toList();
    }
}
