package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.request.EditMessageText;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncMemeServiceTest {

  @Mock private TelegramBot bot;

  @Mock private MemeProcessor memeProcessor;

  @Mock private TelegramFileService fileService;

  @InjectMocks private AsyncMemeService asyncMemeService;

  @Test
  void processAndSaveMemeAsync_Success_Public() {
    // Arrange
    long chatId = 111L;
    long userId = 222L;
    PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
    String description = "Cute puppy";
    String visibilityContext = "PUBLIC";
    int messageIdToEdit = 500;

    when(fileService.getImageFileId(photo)).thenReturn("file-123");
    when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-1");

    // Act
    asyncMemeService.processAndSaveMemeAsync(
        chatId, userId, photo, description, visibilityContext, messageIdToEdit);

    // Assert
    ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
    verify(memeProcessor).save(memeCaptor.capture());
    Meme savedMeme = memeCaptor.getValue();
    assertEquals("file-123", savedMeme.fileId());
    assertEquals("PUBLIC", savedMeme.visibility());
    assertEquals(userId, savedMeme.ownerId());
    assertEquals(0, savedMeme.groupIds().size());

    verify(bot).execute(any(EditMessageText.class));
  }

  @Test
  void processAndSaveMemeAsync_Success_Group() {
    // Arrange
    long chatId = 111L;
    long userId = 222L;
    PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
    String description = "Funny cat";
    String visibilityContext = "GROUP:10,20";
    int messageIdToEdit = 500;

    when(fileService.getImageFileId(photo)).thenReturn("file-456");
    when(memeProcessor.save(any(Meme.class))).thenReturn("meme-uuid-2");

    // Act
    asyncMemeService.processAndSaveMemeAsync(
        chatId, userId, photo, description, visibilityContext, messageIdToEdit);

    // Assert
    ArgumentCaptor<Meme> memeCaptor = ArgumentCaptor.forClass(Meme.class);
    verify(memeProcessor).save(memeCaptor.capture());
    Meme savedMeme = memeCaptor.getValue();
    assertEquals("file-456", savedMeme.fileId());
    assertEquals("GROUP", savedMeme.visibility());
    assertEquals(List.of(10L, 20L), savedMeme.groupIds());

    verify(bot).execute(any(EditMessageText.class));
  }

  @Test
  void processAndSaveMemeAsync_Failure_NullFileId() {
    // Arrange
    long chatId = 111L;
    long userId = 222L;
    PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
    String description = "Funny cat";
    String visibilityContext = "PUBLIC";
    int messageIdToEdit = 500;

    when(fileService.getImageFileId(photo)).thenReturn(null);

    // Act
    asyncMemeService.processAndSaveMemeAsync(
        chatId, userId, photo, description, visibilityContext, messageIdToEdit);

    // Assert
    verify(memeProcessor, never()).save(any(Meme.class));
    verify(bot).execute(any(EditMessageText.class));
  }
}
