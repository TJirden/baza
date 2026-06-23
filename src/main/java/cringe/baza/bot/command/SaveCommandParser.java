package cringe.baza.bot.command;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SaveCommandParser {

    public SaveParseResult parseReplySave(String text) {
        if (text == null || text.isBlank()) {
            return SaveParseResult.success("PUBLIC", null);
        }

        String[] parts = text.split("\\s+");
        String type = parts[0].toLowerCase();

        if (type.equals("private")) {
            return SaveParseResult.success(
                    "PRIVATE", text.substring("private".length()).trim());
        }

        if (type.equals("group")) {
            int i = 1;
            while (i < parts.length && isNumeric(parts[i])) {
                i++;
            }
            if (i == 1) {
                return SaveParseResult.failure("⚠️ Укажите ID групп: /save group {id1} {id2} [описание]");
            }
            String groupIds = Arrays.stream(parts, 1, i).collect(Collectors.joining(","));
            String description = Arrays.stream(parts)
                    .skip(i)
                    .collect(Collectors.joining(" "))
                    .trim();
            return SaveParseResult.success("GROUP:" + groupIds, description);
        }

        if (type.equals("public")) {
            return SaveParseResult.success(
                    "PUBLIC", text.substring("public".length()).trim());
        }

        return SaveParseResult.success("PUBLIC", text);
    }

    public SaveParseResult parseStatefulSave(String text) {
        if (text == null || text.isBlank()) {
            return SaveParseResult.success("PUBLIC", null);
        }

        String[] parts = text.split("\\s+");
        String type = parts[0].toLowerCase();

        if (type.equals("private")) {
            return SaveParseResult.success("PRIVATE", null);
        }

        if (type.equals("group")) {
            int i = 1;
            while (i < parts.length && isNumeric(parts[i])) {
                i++;
            }
            if (i == 1) {
                return SaveParseResult.failure("⚠️ Укажите ID групп: /save group {id1} {id2} [описание]");
            }
            String groupIds = Arrays.stream(parts, 1, i).collect(Collectors.joining(","));
            return SaveParseResult.success("GROUP:" + groupIds, null);
        }

        if (type.equals("public")) {
            return SaveParseResult.success("PUBLIC", null);
        }

        return SaveParseResult.failure(
                "Неверный формат. Используйте: /save, /save private, /save public или /save group 1 2");
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.chars().allMatch(Character::isDigit);
    }
}
