package cringe.baza.model;

import java.awt.image.BufferedImage;

public record Meme(BufferedImage image, String description, long chatId) {
}
