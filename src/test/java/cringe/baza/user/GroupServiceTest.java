package cringe.baza.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.bot.model.GroupActionResult;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private MemeGroupRepository groupRepository;

    @Mock
    private TelegramUserService userService;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroup_SetsOwnerAndMembership() {
        TelegramUser user = new TelegramUser();
        user.setId(1L);
        when(userService.getOrCreateUser(1L, "user", "User")).thenReturn(user);
        when(groupRepository.save(any(MemeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        MemeGroup group = groupService.createGroup(1L, "user", "User", "Котики");

        assertEquals("Котики", group.getName());
        assertEquals(user, group.getOwner());
        assertTrue(group.getMembers().contains(user));
        verify(groupRepository).save(group);
    }

    @Test
    void joinGroup_AddsMemberInsideTransaction() {
        TelegramUser user = new TelegramUser();
        user.setId(2L);
        MemeGroup group = new MemeGroup();
        group.setName("Котики");

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userService.getOrCreateUser(2L, "user2", "User2")).thenReturn(user);
        when(groupRepository.save(any(MemeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupActionResult result = groupService.joinGroup(2L, "user2", "User2", 10L);

        assertEquals(GroupActionResult.Status.OK, result.status());
        assertTrue(group.getMembers().contains(user));
        verify(groupRepository).save(group);
    }

    @Test
    void joinGroup_NotFound() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());

        GroupActionResult result = groupService.joinGroup(2L, "user2", "User2", 10L);

        assertEquals(GroupActionResult.Status.NOT_FOUND, result.status());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void leaveGroup_RemovesMember() {
        TelegramUser user = new TelegramUser();
        user.setId(2L);
        MemeGroup group = new MemeGroup();
        group.setName("Котики");
        group.getMembers().add(user);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(MemeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupActionResult result = groupService.leaveGroup(2L, 10L);

        assertEquals(GroupActionResult.Status.OK, result.status());
        assertTrue(group.getMembers().isEmpty());
        verify(groupRepository).save(group);
    }

    @Test
    void leaveGroup_NotFound() {
        when(groupRepository.findById(10L)).thenReturn(Optional.empty());

        assertEquals(
                GroupActionResult.Status.NOT_FOUND,
                groupService.leaveGroup(2L, 10L).status());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void listGroups_DelegatesToRepository() {
        MemeGroup group = new MemeGroup();
        when(groupRepository.findByMembers_Id(2L)).thenReturn(List.of(group));

        assertEquals(1, groupService.listGroups(2L).size());
        verify(groupRepository).findByMembers_Id(2L);
    }
}
