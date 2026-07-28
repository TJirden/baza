package cringe.baza.meme.phash;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.OptionalLong;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemeImageHasher {

    private static final int HASH_SIZE = 8;
    private static final int DCT_SIZE = 32;

    private static final double[][] DCT_COS = precomputeCosines(DCT_SIZE);
    private static final double[] DCT_ALPHA = precomputeAlphas(DCT_SIZE);

    public OptionalLong computeHash(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return OptionalLong.empty();
        }
        try {
            BufferedImage image;
            synchronized (MemeImageHasher.class) {
                image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            }
            if (image == null) {
                log.warn("ImageIO returned null — unsupported or corrupted image format");
                return OptionalLong.empty();
            }
            BufferedImage gray = resizeToGray(image, DCT_SIZE);
            double[][] pixels = extractPixels(gray);
            double[][] dct = computeDCT2D(pixels);
            return OptionalLong.of(buildHash(dct));
        } catch (Exception e) {
            log.warn("Failed to compute perceptual hash: {}", e.getMessage());
            return OptionalLong.empty();
        }
    }

    private static double[][] precomputeCosines(int n) {
        double[][] cos = new double[n][n];
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                cos[k][i] = Math.cos((2 * i + 1) * k * Math.PI / (2.0 * n));
            }
        }
        return cos;
    }

    private static double[] precomputeAlphas(int n) {
        double[] alpha = new double[n];
        alpha[0] = Math.sqrt(1.0 / n);
        for (int k = 1; k < n; k++) {
            alpha[k] = Math.sqrt(2.0 / n);
        }
        return alpha;
    }

    private static BufferedImage resizeToGray(BufferedImage src, int size) {
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static double[][] extractPixels(BufferedImage grayImage) {
        int n = grayImage.getWidth();
        double[][] pixels = new double[n][n];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                pixels[x][y] = grayImage.getRaster().getSample(x, y, 0);
            }
        }
        return pixels;
    }

    private static double[][] computeDCT2D(double[][] f) {
        int n = f.length;
        double[][] result = new double[n][n];
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                double sum = 0;
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        sum += f[x][y] * DCT_COS[u][x] * DCT_COS[v][y];
                    }
                }
                result[u][v] = DCT_ALPHA[u] * DCT_ALPHA[v] * sum;
            }
        }
        return result;
    }

    private static long buildHash(double[][] dct) {
        double sum = 0;
        int count = 0;
        for (int u = 0; u < HASH_SIZE; u++) {
            for (int v = 0; v < HASH_SIZE; v++) {
                if (u == 0 && v == 0) {
                    continue;
                }
                sum += dct[u][v];
                count++;
            }
        }
        double mean = sum / count;

        long hash = 0;
        int bitIndex = 0;
        for (int u = 0; u < HASH_SIZE; u++) {
            for (int v = 0; v < HASH_SIZE; v++) {
                if (u == 0 && v == 0) {
                    continue;
                }
                if (dct[u][v] > mean) {
                    hash |= 1L << bitIndex;
                }
                bitIndex++;
            }
        }
        return hash;
    }
}
