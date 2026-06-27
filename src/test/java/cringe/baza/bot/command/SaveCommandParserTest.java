package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SaveCommandParserTest {

    private final SaveCommandParser parser = new SaveCommandParser();

    @Test
    void parseReplySave_NullOrEmpty_ReturnsPublic() {
        String text = "";

        SaveParseResult result = parser.parseReplySave(text);

        assertTrue(result.success());
        assertEquals("PUBLIC", result.visibility());
        assertNull(result.description());
    }

    @Test
    void parseReplySave_Private_ReturnsPrivateWithDescription() {
        String text = "private cute dog";

        SaveParseResult result = parser.parseReplySave(text);

        assertTrue(result.success());
        assertEquals("PRIVATE", result.visibility());
        assertEquals("cute dog", result.description());
    }

    @Test
    void parseReplySave_GroupNoIds_ReturnsFailure() {
        String text = "group description only";

        SaveParseResult result = parser.parseReplySave(text);

        assertFalse(result.success());
        assertEquals("Укажите ID групп: /save group {id1} {id2} [описание]", result.errorMessage());
    }

    @Test
    void parseReplySave_GroupWithIds_ReturnsGroupWithDescription() {
        String text = "group 123 456 cute dog";

        SaveParseResult result = parser.parseReplySave(text);

        assertTrue(result.success());
        assertEquals("GROUP:123,456", result.visibility());
        assertEquals("cute dog", result.description());
    }

    @Test
    void parseStatefulSave_GroupWithIds_ReturnsGroupNullDescription() {
        String text = "group 123 456";

        SaveParseResult result = parser.parseStatefulSave(text);

        assertTrue(result.success());
        assertEquals("GROUP:123,456", result.visibility());
        assertNull(result.description());
    }

    @Test
    void parseStatefulSave_InvalidType_ReturnsFailure() {
        String text = "invalidType";

        SaveParseResult result = parser.parseStatefulSave(text);

        assertFalse(result.success());
        assertEquals(
                "Неверный формат. Используйте: /save, /save private, /save public или /save group 1 2",
                result.errorMessage());
    }
}
