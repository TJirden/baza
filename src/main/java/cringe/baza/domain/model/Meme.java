package cringe.baza.domain.model;

import java.awt.image.BufferedImage;

public record Meme(BufferedImage image, String description, long chatId) {
}
