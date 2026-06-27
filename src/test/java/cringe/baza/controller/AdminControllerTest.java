package cringe.baza.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cringe.baza.meme.MemeModerationService;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramUserRepository userRepository;

    @MockitoBean
    private MemeGroupRepository groupRepository;

    @MockitoBean
    private MemeProcessor memeProcessor;

    @MockitoBean
    private MemeModerationService moderationService;

    @Test
    void listMemes() throws Exception {
        Meme meme = new Meme("1", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(memeProcessor.getAll(10, 5)).thenReturn(List.of(meme));

        mockMvc.perform(get("/api/admin/memes")
                        .param("limit", "10")
                        .param("offset", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void deleteMeme_Success() throws Exception {
        when(memeProcessor.delete("meme-123")).thenReturn(true);

        mockMvc.perform(delete("/api/admin/memes/meme-123")).andExpect(status().isOk());
    }

    @Test
    void deleteMeme_NotFound() throws Exception {
        when(memeProcessor.delete("meme-123")).thenReturn(false);

        mockMvc.perform(delete("/api/admin/memes/meme-123")).andExpect(status().isNotFound());
    }

    @Test
    void updateMeme_Success() throws Exception {
        when(memeProcessor.update("meme-123", "new description")).thenReturn(true);

        mockMvc.perform(patch("/api/admin/memes/meme-123")
                        .content("new description")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk());
    }

    @Test
    void updateMeme_NotFound() throws Exception {
        when(memeProcessor.update("meme-123", "new description")).thenReturn(false);

        mockMvc.perform(patch("/api/admin/memes/meme-123")
                        .content("new description")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void listUsers() throws Exception {
        TelegramUser user = new TelegramUser(123L, "user1", "User One", 0, 0, java.util.Collections.emptySet());
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("user1"));
    }

    @Test
    void listGroups() throws Exception {
        MemeGroup group = new MemeGroup(10L, "Group Ten", null, java.util.Collections.emptySet());
        when(groupRepository.findAll()).thenReturn(List.of(group));

        mockMvc.perform(get("/api/admin/groups").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Group Ten"));
    }

    @Test
    void deleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/123")).andExpect(status().isOk());
        verify(userRepository).deleteById(123L);
    }

    @Test
    void deleteGroup() throws Exception {
        mockMvc.perform(delete("/api/admin/groups/10")).andExpect(status().isOk());
        verify(groupRepository).deleteById(10L);
    }

    @Test
    void searchMemes() throws Exception {
        Meme meme = new Meme("1", "desc", "ocr", "file", 1L, MemeVisibility.PUBLIC, List.of());
        when(memeProcessor.searchWithIds("query", 50, null, null)).thenReturn(List.of(meme));

        mockMvc.perform(get("/api/admin/memes/search").param("q", "query").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void searchUsers() throws Exception {
        TelegramUser user = new TelegramUser(123L, "user1", "User One", 0, 0, java.util.Collections.emptySet());
        when(userRepository.findByUsernameContainingIgnoreCaseOrFirstNameContainingIgnoreCase("query", "query"))
                .thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users/search").param("q", "query").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("user1"));
    }

    @Test
    void searchGroups() throws Exception {
        MemeGroup group = new MemeGroup(10L, "Group Ten", null, java.util.Collections.emptySet());
        when(groupRepository.findByNameContainingIgnoreCase("query")).thenReturn(List.of(group));

        mockMvc.perform(get("/api/admin/groups/search").param("q", "query").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Group Ten"));
    }

    @Test
    void getQuarantined() throws Exception {
        MemeModeration mod = new MemeModeration();
        mod.setId("meme-1");
        mod.setDescription("Quarantined description");
        when(moderationService.getQuarantinedMemes()).thenReturn(List.of(mod));

        mockMvc.perform(get("/api/admin/moderation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("meme-1"));
    }

    @Test
    void approveQuarantined_Success() throws Exception {
        when(moderationService.approveMeme("meme-1")).thenReturn(true);

        mockMvc.perform(post("/api/admin/moderation/meme-1/approve")).andExpect(status().isOk());
    }

    @Test
    void approveQuarantined_NotFound() throws Exception {
        when(moderationService.approveMeme("meme-1")).thenReturn(false);

        mockMvc.perform(post("/api/admin/moderation/meme-1/approve")).andExpect(status().isNotFound());
    }

    @Test
    void rejectQuarantined_Success() throws Exception {
        when(moderationService.rejectMeme("meme-1", "bad content")).thenReturn(true);

        mockMvc.perform(post("/api/admin/moderation/meme-1/reject")
                        .content("bad content")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk());
    }

    @Test
    void rejectQuarantined_NotFound() throws Exception {
        when(moderationService.rejectMeme("meme-1", "bad content")).thenReturn(false);

        mockMvc.perform(post("/api/admin/moderation/meme-1/reject")
                        .content("bad content")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
    }
}
