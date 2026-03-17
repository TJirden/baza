package cringe.baza.presentation.controller;

import cringe.baza.domain.service.MemeService;
import cringe.baza.presentation.dto.MemeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/memes")
@RequiredArgsConstructor
@Slf4j
public class MemeController {

    private final MemeService memeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MemeResponse> uploadMeme(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {

        try {
            log.info("Uploading meme: {}, description: {}", file.getOriginalFilename(), description);
            MemeResponse response = memeService.uploadMeme(file, description);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading meme: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<MemeResponse>> getAllMemes() {
        return ResponseEntity.ok(memeService.getAllMemes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemeResponse> getMeme(@PathVariable String id) {
        log.info("Getting meme with id: {}", id);
        MemeResponse response = memeService.getMeme(id);
        if (response == null) {
            log.warn("Meme not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getMemeImage(@PathVariable String id) {
        try {
            log.info("Getting image for meme id: {}", id);
            byte[] image = memeService.getMemeImage(id);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(image);
        } catch (Exception e) {
            log.error("Error getting image: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemeResponse>> searchMemes(@RequestParam String q) {
        log.info("Searching memes with query: {}", q);
        return ResponseEntity.ok(memeService.searchMemes(q));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeme(@PathVariable String id) {
        try {
            log.info("Deleting meme with id: {}", id);
            memeService.deleteMeme(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting meme: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}