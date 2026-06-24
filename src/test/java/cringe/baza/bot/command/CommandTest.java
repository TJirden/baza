package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import org.junit.jupiter.api.Test;

class CommandTest {

    private final Command testCommand = new Command() {
        @Override
        public String command() {
            return "find";
        }

        @Override
        public String description() {
            return "Find meme";
        }

        @Override
        public BaseRequest<?, ?> handle(Update update) {
            return null;
        }
    };

    @Test
    void extractText_SingleWord() {
        String result = testCommand.extractText("/find keyword");
        assertEquals("keyword", result);
    }

    @Test
    void extractText_MultipleWords() {
        String result = testCommand.extractText("/find sad cat playing");
        assertEquals("sad cat playing", result);
    }

    @Test
    void extractText_Empty() {
        String result = testCommand.extractText("/find");
        assertNull(result);
    }

    @Test
    void extractText_WithBotName() {
        String result = testCommand.extractText("/find@cringe_baza_bot sad cat playing");
        assertEquals("sad cat playing", result);
    }

    @Test
    void supports_Matches() {
        assertTrue(testCommand.supports("/find"));
        assertTrue(testCommand.supports("/find keyword"));
        assertTrue(testCommand.supports("/find@cringe_baza_bot"));
    }

    @Test
    void supports_NoMatch() {
        assertFalse(testCommand.supports("/delete"));
        assertFalse(testCommand.supports(null));
    }
}
