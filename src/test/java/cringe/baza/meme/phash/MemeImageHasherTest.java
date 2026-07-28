package cringe.baza.meme.phash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.OptionalLong;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MemeImageHasherTest {

    private final MemeImageHasher hasher = new MemeImageHasher();

    @Test
    void computeHash_SameBytes_SameHash() throws IOException {
        byte[] imageBytes = createGradientPng(100, 100);

        OptionalLong hash1 = hasher.computeHash(imageBytes);
        OptionalLong hash2 = hasher.computeHash(imageBytes);

        assertTrue(hash1.isPresent());
        assertTrue(hash2.isPresent());
        assertEquals(hash1.getAsLong(), hash2.getAsLong());
    }

    @Test
    void computeHash_Resized_SimilarHash() throws IOException {
        byte[] smallBytes = createGradientPng(100, 100);
        byte[] largeBytes = createGradientPng(200, 200);

        OptionalLong smallHash = hasher.computeHash(smallBytes);
        OptionalLong largeHash = hasher.computeHash(largeBytes);

        assertTrue(smallHash.isPresent());
        assertTrue(largeHash.isPresent());

        int hammingDistance = Long.bitCount(smallHash.getAsLong() ^ largeHash.getAsLong());
        assertTrue(
                hammingDistance <= 5, "Expected Hamming distance <= 5 for resized image, but was " + hammingDistance);
    }

    @Test
    void computeHash_DifferentImages_LargeHammingDistance() throws IOException {
        byte[] gradientBytes = createGradientPng(100, 100);
        byte[] solidBytes = createSolidPng(100, 100, Color.WHITE);

        OptionalLong hash1 = hasher.computeHash(gradientBytes);
        OptionalLong hash2 = hasher.computeHash(solidBytes);

        assertTrue(hash1.isPresent());
        assertTrue(hash2.isPresent());

        int hammingDistance = Long.bitCount(hash1.getAsLong() ^ hash2.getAsLong());
        assertTrue(
                hammingDistance > 5, "Expected Hamming distance > 5 for different images, but was " + hammingDistance);
    }

    @Test
    void computeHash_JpegReencode_SimilarHash() throws IOException {
        byte[] pngBytes = createGradientPng(200, 200);
        byte[] jpegBytes = createGradientJpeg(200, 200);

        OptionalLong pngHash = hasher.computeHash(pngBytes);
        OptionalLong jpegHash = hasher.computeHash(jpegBytes);

        assertTrue(pngHash.isPresent());
        assertTrue(jpegHash.isPresent());

        int hammingDistance = Long.bitCount(pngHash.getAsLong() ^ jpegHash.getAsLong());
        assertTrue(
                hammingDistance <= 5, "Expected Hamming distance <= 5 for JPEG re-encode, but was " + hammingDistance);
    }

    @Test
    void computeHash_InvalidBytes_Empty() {
        OptionalLong result = hasher.computeHash(new byte[] {1, 2, 3});
        assertTrue(result.isEmpty());
    }

    @Test
    void computeHash_NullBytes_Empty() {
        OptionalLong result = hasher.computeHash(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void computeHash_EmptyBytes_Empty() {
        OptionalLong result = hasher.computeHash(new byte[0]);
        assertTrue(result.isEmpty());
    }

    private byte[] createGradientPng(int width, int height) throws IOException {
        return createGradientImage(width, height, "png");
    }

    private byte[] createGradientJpeg(int width, int height) throws IOException {
        return createGradientImage(width, height, "jpeg");
    }

    private byte[] createGradientImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int r = x * 255 / width;
                    int gr = y * 255 / height;
                    g.setColor(new Color(r, gr, 128));
                    g.fillRect(x, y, 1, 1);
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private byte[] createSolidPng(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
