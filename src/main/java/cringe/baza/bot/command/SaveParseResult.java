package cringe.baza.bot.command;

public record SaveParseResult(boolean success, String visibility, String description, String errorMessage) {
    public static SaveParseResult success(String visibility, String description) {
        return new SaveParseResult(true, visibility, description, null);
    }

    public static SaveParseResult failure(String errorMessage) {
        return new SaveParseResult(false, null, null, errorMessage);
    }
}
