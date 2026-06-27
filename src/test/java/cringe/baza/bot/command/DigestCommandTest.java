package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.meme.MemeDigestService;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigestCommandTest {

    @Mock
    private MemeDigestService digestService;

    @Mock
    private MemeGroupRepository groupRepository;

    @Mock
    private TelegramUserRepository userRepository;

    @InjectMocks
    private DigestCommand digestCommand;

    @Test
    void commandAndDescription() {
        assertEquals("digest", digestCommand.command());
        assertNotNull(digestCommand.description());
    }

    @Test
    void handle_UserNotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        when(userRepository.findById(200L)).thenReturn(Optional.empty());

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("не зарегистрированы"));
    }

    @Test
    void handle_UserNotInAnyGroups() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        TelegramUser telegramUser = new TelegramUser();
        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of());

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("не состоите ни в одной группе"));
    }

    @Test
    void handle_ParamProvided_NonNumeric() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest abc");

        TelegramUser telegramUser = new TelegramUser();
        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(new MemeGroup()));

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("должен быть числом"));
    }

    @Test
    void handle_ParamProvided_NotMemberOfGroup() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest 999");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("не состоите в группе с ID 999"));
    }

    @Test
    void handle_ParamProvided_NoNewMemes() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest 111");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));
        when(digestService.getTopMemesForGroup(111L)).thenReturn(List.of());

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("нет новых мемов"));
    }

    @Test
    void handle_ParamProvided_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest 111");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));

        List<MemeModeration> topMemes = List.of(new MemeModeration());
        when(digestService.getTopMemesForGroup(111L)).thenReturn(topMemes);
        when(digestService.generateDigestTextWithAI("Group One", topMemes)).thenReturn("AI Text");

        assertNull(digestCommand.handle(update));
        verify(digestService).sendDigestToUser(200L, "AI Text", topMemes);
    }

    @Test
    void handle_ParamProvided_Exception() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest 111");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));
        when(digestService.getTopMemesForGroup(111L)).thenThrow(new RuntimeException("DB error"));

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Произошла ошибка при генерации"));
    }

    @Test
    void handle_NoParam_SingleGroup_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));

        List<MemeModeration> topMemes = List.of(new MemeModeration());
        when(digestService.getTopMemesForGroup(111L)).thenReturn(topMemes);
        when(digestService.generateDigestTextWithAI("Group One", topMemes)).thenReturn("AI Text");

        assertNull(digestCommand.handle(update));
        verify(digestService).sendDigestToUser(200L, "AI Text", topMemes);
    }

    @Test
    void handle_NoParam_SingleGroup_NoNewMemes() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));
        when(digestService.getTopMemesForGroup(111L)).thenReturn(List.of());

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("нет новых мемов"));
    }

    @Test
    void handle_NoParam_SingleGroup_Exception() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup group = new MemeGroup();
        group.setId(111L);
        group.setName("Group One");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(group));
        when(digestService.getTopMemesForGroup(111L)).thenThrow(new RuntimeException("DB error"));

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Произошла ошибка при генерации"));
    }

    @Test
    void handle_NoParam_MultipleGroups() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/digest");

        TelegramUser telegramUser = new TelegramUser();
        MemeGroup g1 = new MemeGroup();
        g1.setId(111L);
        g1.setName("Group One");
        MemeGroup g2 = new MemeGroup();
        g2.setId(222L);
        g2.setName("Group Two");

        when(userRepository.findById(200L)).thenReturn(Optional.of(telegramUser));
        when(groupRepository.findByMembersContains(telegramUser)).thenReturn(List.of(g1, g2));

        SendMessage response = (SendMessage) digestCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Вы состоите в нескольких группах"));
    }
}
