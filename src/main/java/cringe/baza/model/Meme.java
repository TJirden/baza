package cringe.baza.model;

import java.util.List;

public record Meme(String description, String fileId, Long ownerId, String visibility, List<Long> groupIds) {
}
