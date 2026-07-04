package cringe.baza.exception;

public class CensorshipUnavailableException extends RuntimeException {
    public CensorshipUnavailableException(String message) {
        super(message);
    }

    public CensorshipUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
