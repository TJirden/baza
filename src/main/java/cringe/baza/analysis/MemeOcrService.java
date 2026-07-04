package cringe.baza.analysis;

@FunctionalInterface
public interface MemeOcrService {
    String extractText(String fileId);
}
