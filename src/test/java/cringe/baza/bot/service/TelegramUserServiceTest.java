package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramUserServiceTest {

    @Mock
    private TelegramUserRepository userRepository;

    @Mock
    private MemeGroupRepository groupRepository;

    @InjectMocks
    private TelegramUserService userService;

    @Test
    void getOrCreateUser_NewUser_Created() {
        Long userId = 123L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));

        TelegramUser result = userService.getOrCreateUser(userId, "new_nick", "NewName");

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("new_nick", result.getUsername());
        assertEquals("NewName", result.getFirstName());
        verify(userRepository).save(any(TelegramUser.class));
    }

    @Test
    void getOrCreateUser_ExistingUser_NoChanges() {
        Long userId = 123L;
        TelegramUser existing = new TelegramUser();
        existing.setId(userId);
        existing.setUsername("old_nick");
        existing.setFirstName("OldName");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        TelegramUser result = userService.getOrCreateUser(userId, "old_nick", "OldName");

        assertSame(existing, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrCreateUser_ExistingUser_UpdatedUsername() {
        Long userId = 123L;
        TelegramUser existing = new TelegramUser();
        existing.setId(userId);
        existing.setUsername("old_nick");
        existing.setFirstName("OldName");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));

        TelegramUser result = userService.getOrCreateUser(userId, "new_nick", "OldName");

        assertEquals("new_nick", result.getUsername());
        assertEquals("OldName", result.getFirstName());
        verify(userRepository).save(existing);
    }

    @Test
    void getOrCreateUser_ExistingUser_UpdatedFirstName() {
        Long userId = 123L;
        TelegramUser existing = new TelegramUser();
        existing.setId(userId);
        existing.setUsername("old_nick");
        existing.setFirstName("OldName");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));

        TelegramUser result = userService.getOrCreateUser(userId, "old_nick", "NewName");

        assertEquals("old_nick", result.getUsername());
        assertEquals("NewName", result.getFirstName());
        verify(userRepository).save(existing);
    }

    @Test
    void getOrCreateUser_ExistingUser_NullParamsIgnored() {
        Long userId = 123L;
        TelegramUser existing = new TelegramUser();
        existing.setId(userId);
        existing.setUsername("old_nick");
        existing.setFirstName("OldName");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        TelegramUser result = userService.getOrCreateUser(userId, null, null);

        assertSame(existing, result);
        assertEquals("old_nick", result.getUsername());
        assertEquals("OldName", result.getFirstName());
        verify(userRepository, never()).save(any());
    }
}
