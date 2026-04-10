package cringe.baza.bot.imaginator;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class BasicImageManager implements ImageManager {

    private final Map<String, Meme> memeStorage = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> userMemeIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public String saveImage(BufferedImage image, String description, Long chatId) {
        if (image == null || chatId == null) {
            throw new IllegalArgumentException("Image and chatId cannot be null");
        }

        String id = generateId();
        Meme meme = new Meme(image, description != null ? description : "", chatId);

        memeStorage.put(id, meme);
        userMemeIndex.computeIfAbsent(chatId, k -> new ArrayList<>()).add(id);

        return id;
    }

    @Override
    public Optional<Meme> getMeme(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(memeStorage.get(id));
    }

    @Override
    public List<Meme> getUserMemes(Long chatId) {
        if (chatId == null) {
            return List.of();
        }

        List<String> memeIds = userMemeIndex.getOrDefault(chatId, List.of());
        return memeIds.stream()
                .map(memeStorage::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meme> getRecentUserMemes(Long chatId, int limit) {
        if (chatId == null || limit <= 0) {
            return List.of();
        }

        List<String> memeIds = userMemeIndex.getOrDefault(chatId, List.of());

        return memeIds.stream()
                .sorted(Collections.reverseOrder())
                .limit(limit)
                .map(memeStorage::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteMeme(String id) {
        if (id == null) {
            return false;
        }

        Meme removed = memeStorage.remove(id);
        if (removed != null) {
            List<String> userMemes = userMemeIndex.get(removed.chatId());
            if (userMemes != null) {
                userMemes.remove(id);
                if (userMemes.isEmpty()) {
                    userMemeIndex.remove(removed.chatId());
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean updateDescription(String id, String newDescription) {
        if (id == null || newDescription == null) {
            return false;
        }

        Meme existing = memeStorage.get(id);
        if (existing != null) {
            Meme updated = new Meme(
                    existing.image(),
                    newDescription,
                    existing.chatId()
            );
            memeStorage.put(id, updated);
            return true;
        }
        return false;
    }

    private String generateId() {
        return String.valueOf(idGenerator.getAndIncrement());
    }

    public int getTotalMemeCount() {
        return memeStorage.size();
    }

    public void clearAllMemes() {
        memeStorage.clear();
        userMemeIndex.clear();
        idGenerator.set(1);
    }
}