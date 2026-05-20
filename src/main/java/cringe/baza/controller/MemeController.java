package cringe.baza.controller;

import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramUserService;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memes")
@RequiredArgsConstructor
public class MemeController {

    private final MemeProcessor memeProcessor;
    private final TelegramFileService fileService;
    private final TelegramUserService userService;

    @GetMapping("/search")
    public List<Meme> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long userId) {

        List<Long> groupIds = new ArrayList<>();
        if (userId != null) {
            TelegramUser user = userService.getOrCreateUser(userId, null, null);
        }

        return memeProcessor.getMemesByDescription(q, limit, userId, groupIds);
    }

    @GetMapping("/image/{fileId}")
    public ResponseEntity<InputStreamResource> getImage(@PathVariable String fileId) {
        try {
            InputStream is = fileService.downloadFile(fileId);
            if (is == null) return ResponseEntity.notFound().build();

            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(new InputStreamResource(is));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
