package cringe.baza.repository;

import cringe.baza.model.Meme;
import cringe.baza.model.MemeRepository;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class S3MemeRepository implements MemeRepository {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    private static final String MEMES_PREFIX = "memes/";
    private static final String IMAGE_FORMAT = "png";

    @Override
    public void put(String id, Meme meme) {
        String objectName = buildObjectName(id);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(meme.image(), IMAGE_FORMAT, baos);
            byte[] imageBytes = baos.toByteArray();

            Map<String, String> userMetadata = Map.of(
                    "description", meme.description(),
                    "fileId", String.valueOf(meme.fileId()),
                    "imageFormat", IMAGE_FORMAT
            );

            try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
                PutObjectArgs args = PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, imageBytes.length, -1)
                        .contentType("image/" + IMAGE_FORMAT)
                        .userMetadata(userMetadata)
                        .build();

                minioClient.putObject(args);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to put meme to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Meme> get(String id) {
        String objectName = buildObjectName(id);

        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            try (InputStream stream = minioClient.getObject(args)) {
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                );

                BufferedImage image = ImageIO.read(stream);

                if (image == null) {
                    log.error("Failed to read image from MinIO");
                    throw new RuntimeException("Failed to read image from MinIO");
                }

                String description = stat.userMetadata().getOrDefault("description", "");
                String fileId = stat.userMetadata().getOrDefault("fileId", "0");

                Meme meme = new Meme(image, description, fileId);
                return Optional.of(meme);
            }

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return Optional.empty();
            }
            log.error("Failed to get meme from MinIO", e);
            throw new RuntimeException("Failed to get meme from MinIO", e);
        } catch (Exception e) {
            log.error("Failed to get meme from MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get meme from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Meme> remove(String id) {
        Optional<Meme> meme = get(id);

        if (meme.isPresent()) {
            String objectName = buildObjectName(id);

            try {
                RemoveObjectArgs args = RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build();

                minioClient.removeObject(args);
            } catch (Exception e) {
                log.error("Failed to remove meme from MinIO", e);
                throw new RuntimeException("Failed to remove meme from MinIO", e);
            }
        }

        return meme;
    }

    @Override
    public int size() {
        try {
            ListObjectsArgs args = ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(MEMES_PREFIX)
                    .recursive(true)
                    .build();

            Iterable<Result<Item>> results = minioClient.listObjects(args);
            int count = 0;
            for (Result<Item> _ : results) {
                count++;
            }
            return count;
        } catch (Exception e) {
            log.error("Exception while calc size", e);
            return 0;
        }
    }

    @Override
    public int clear() {
        try {
            ListObjectsArgs listArgs = ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(MEMES_PREFIX)
                    .recursive(true)
                    .build();

            Iterable<Result<Item>> results = minioClient.listObjects(listArgs);
            int deletedCount = 0;

            for (Result<Item> result : results) {
                Item item = result.get();
                RemoveObjectArgs removeArgs = RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(item.objectName())
                        .build();

                minioClient.removeObject(removeArgs);
                deletedCount++;
            }

            return deletedCount;

        } catch (Exception e) {
            log.error("Failed to clear MinIO bucket", e);
            throw new RuntimeException("Failed to clear MinIO bucket", e);
        }
    }

    private String buildObjectName(String id) {
        return MEMES_PREFIX + id + "." + IMAGE_FORMAT;
    }
}
