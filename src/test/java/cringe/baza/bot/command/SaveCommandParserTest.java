package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SaveCommandParserTest {

    private final SaveCommandParser parser = new SaveCommandParser();

    @Test
    void parseReplySave_NullOrEmpty_ReturnsPublic() {
        // Arrange
        String text = "";

        // Act
        SaveParseResult result = parser.parseReplySave(text);

        // Assert
        assertTrue(result.success());
        assertEquals("PUBLIC", result.visibility());
        assertNull(result.description());
    }

    @Test
    void parseReplySave_Private_ReturnsPrivateWithDescription() {
        // Arrange
        String text = "private cute dog";

        // Act
        SaveParseResult result = parser.parseReplySave(text);

        // Assert
        assertTrue(result.success());
        assertEquals("PRIVATE", result.visibility());
        assertEquals("cute dog", result.description());
    }

    @Test
    void parseReplySave_GroupNoIds_ReturnsFailure() {
        // Arrange
        String text = "group description only";

        // Act
        SaveParseResult result = parser.parseReplySave(text);

        // Assert
        assertFalse(result.success());
        assertEquals("⚠️ Укажите ID групп: /save group {id1} {id2} [описание]", result.errorMessage());
    }

    @Test
    void parseReplySave_GroupWithIds_ReturnsGroupWithDescription() {
        // Arrange
        String text = "group 123 456 cute dog";

        // Act
        SaveParseResult result = parser.parseReplySave(text);

        // Assert
        assertTrue(result.success());
        assertEquals("GROUP:123,456", result.visibility());
        assertEquals("cute dog", result.description());
    }

    @Test
    void parseStatefulSave_GroupWithIds_ReturnsGroupNullDescription() {
        // Arrange
        String text = "group 123 456";

        // Act
        SaveParseResult result = parser.parseStatefulSave(text);

        // Assert
        assertTrue(result.success());
        assertEquals("GROUP:123,456", result.visibility());
        assertNull(result.description());
    }

    @Test
    void parseStatefulSave_InvalidType_ReturnsFailure() {
        // Arrange
        String text = "invalidType";

        // Act
        SaveParseResult result = parser.parseStatefulSave(text);

        // Assert
        assertFalse(result.success());
        assertEquals(
                "Неверный формат. Используйте: /save, /save private, /save public или /save group 1 2",
                result.errorMessage());
    }
}
