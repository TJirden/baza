package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelpCommand implements Command {

    private final List<Command> commands;

    @Override
    public String command() {
        return "help";
    }

    @Override
    public String description() {
        return "Список команд";
    }

    @Override
    public SendMessage handle(Update update) {
        long chatId = update.message().chat().id();
        StringBuilder sb = new StringBuilder("Доступные команды:\n");
        for (Command cmd : commands) {
            if (cmd == this) {
                continue;
            }
            sb.append("/")
                    .append(cmd.command())
                    .append(" — ")
                    .append(cmd.description())
                    .append("\n");
        }
        return new SendMessage(chatId, sb.toString().trim());
    }
}
