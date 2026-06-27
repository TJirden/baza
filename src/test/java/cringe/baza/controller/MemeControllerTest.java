package cringe.baza.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramUserService;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.processor.MemeProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemeController.class)
class MemeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemeProcessor memeProcessor;

    @MockitoBean
    private TelegramFileService fileService;

    @MockitoBean
    private TelegramUserService userService;

    @Test
    void search_WithoutUserId_ReturnsMemes() throws Exception {
        Meme meme = new Meme("1", "description", "ocr", "fileId", 123L, MemeVisibility.PUBLIC, List.of());
        when(memeProcessor.getMemesByDescription("test", 20, null, List.of())).thenReturn(List.of(meme));

        mockMvc.perform(get("/api/memes/search").param("q", "test").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].description").value("description"));
    }

    @Test
    void search_WithUserId_FiltersByGroups() throws Exception {
        when(userService.getUserGroupIds(123L)).thenReturn(List.of(10L, 20L));
        Meme meme = new Meme("1", "description", "ocr", "fileId", 123L, MemeVisibility.GROUP, List.of(10L, 20L));
        when(memeProcessor.getMemesByDescription("test", 20, 123L, List.of(10L, 20L)))
                .thenReturn(List.of(meme));

        mockMvc.perform(get("/api/memes/search")
                        .param("q", "test")
                        .param("userId", "123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void getImage_Success() throws Exception {
        java.io.ByteArrayInputStream is = new java.io.ByteArrayInputStream("image-data".getBytes());
        when(fileService.downloadFile("file-123")).thenReturn(is);

        mockMvc.perform(get("/api/memes/image/file-123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("image-data".getBytes()));
    }

    @Test
    void getImage_NotFound() throws Exception {
        when(fileService.downloadFile("file-123")).thenReturn(null);

        mockMvc.perform(get("/api/memes/image/file-123")).andExpect(status().isNotFound());
    }

    @Test
    void getImage_Exception_ReturnsInternalServerError() throws Exception {
        when(fileService.downloadFile("file-123")).thenThrow(new RuntimeException("File service failed"));

        mockMvc.perform(get("/api/memes/image/file-123")).andExpect(status().isInternalServerError());
    }
}
