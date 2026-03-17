package cringe.baza.domain.service;

import cringe.baza.data.MinioService;
import cringe.baza.domain.model.Meme;
import cringe.baza.domain.repository.MemeRepository;
import cringe.baza.presentation.dto.MemeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemeService {

    private final MemeRepository memeRepository;
    private final MinioService minioService;

    public MemeResponse uploadMeme(MultipartFile file, String description) {
        try {
            String objectName = generateObjectName(file.getOriginalFilename());

            log.info("Uploading file {} to MinIO", objectName);
            minioService.uploadFile(file, objectName);

            Meme meme = Meme.builder()
                    .filename(file.getOriginalFilename())
                    .description(description)
                    .s3Key(objectName)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();

            Meme savedMeme = memeRepository.save(meme);
            log.info("Meme saved with id: {}", savedMeme.getId());

            return convertToResponse(savedMeme);
        } catch (Exception e) {
            log.error("Error uploading meme: {}", e.getMessage());
            throw new RuntimeException("Error uploading meme: " + e.getMessage());
        }
    }

    public List<MemeResponse> getAllMemes() {
        return memeRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public MemeResponse getMeme(String id) {
        return memeRepository.findById(id)
                .map(this::convertToResponse)
                .orElse(null);
    }

    public byte[] getMemeImage(String id) {
        Meme meme = memeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meme not found with id: " + id));

        return minioService.getFile(meme.getS3Key());
    }

    public List<MemeResponse> searchMemes(String query) {
        return memeRepository.searchByDescription(query)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public void deleteMeme(String id) {
        Meme meme = memeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meme not found with id: " + id));

        minioService.deleteFile(meme.getS3Key());
        memeRepository.deleteById(id);
        log.info("Meme deleted with id: {}", id);
    }

    private String generateObjectName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }

    private MemeResponse convertToResponse(Meme meme) {
        return new MemeResponse(
                meme.getId(),
                meme.getFilename(),
                meme.getDescription(),
                minioService.getFileUrl(meme.getS3Key()),
                meme.getCreatedAt(),
                meme.getFileSize()
        );
    }
}