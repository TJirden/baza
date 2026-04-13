package cringe.baza.bot.imaginator;

import java.awt.image.BufferedImage;

public record Meme(BufferedImage image,String description, long chatId) {
}
