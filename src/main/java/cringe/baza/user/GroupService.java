package cringe.baza.user;

import cringe.baza.bot.model.GroupActionResult;
import cringe.baza.domain.MemeGroup;
import cringe.baza.repository.jpa.MemeGroupRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final MemeGroupRepository groupRepository;
    private final TelegramUserService userService;

    @Transactional
    public MemeGroup createGroup(long userId, String username, String firstName, String name) {
        var user = userService.getOrCreateUser(userId, username, firstName);
        MemeGroup group = new MemeGroup();
        group.setName(name);
        group.setOwner(user);
        group.getMembers().add(user);
        return groupRepository.save(group);
    }

    @Transactional
    public GroupActionResult joinGroup(long userId, String username, String firstName, long groupId) {
        return groupRepository
                .findById(groupId)
                .map(group -> {
                    var user = userService.getOrCreateUser(userId, username, firstName);
                    group.getMembers().add(user);
                    return GroupActionResult.ok(groupRepository.save(group));
                })
                .orElseGet(GroupActionResult::notFound);
    }

    @Transactional
    public GroupActionResult leaveGroup(long userId, long groupId) {
        return groupRepository
                .findById(groupId)
                .map(group -> {
                    group.getMembers().removeIf(member -> member.getId().equals(userId));
                    return GroupActionResult.ok(groupRepository.save(group));
                })
                .orElseGet(GroupActionResult::notFound);
    }

    @Transactional(readOnly = true)
    public List<MemeGroup> listGroups(long userId) {
        return groupRepository.findByMembers_Id(userId);
    }
}
