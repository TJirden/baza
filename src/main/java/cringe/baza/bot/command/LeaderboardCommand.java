package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LeaderboardCommand implements Command {

    private final MemeRatingRepository memeRatingRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final MemeModerationRepository memeModerationRepository;

    @Override
    public String command() {
        return "leaderboard";
    }

    @Override
    public String description() {
        return "Показать таблицу лидеров баттлов мемов";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();

        List<MemeRating> topRatings = memeRatingRepository.findTop5ByOrderByEloRatingDesc();
        List<TelegramUser> topUsers = telegramUserRepository.findTop5ByOrderByPointsDesc();

        if (topRatings.isEmpty() && topUsers.isEmpty()) {
            return new SendMessage(
                            chatId,
                            "🏆 *Таблица лидеров пуста!*\nНачните баттлы с помощью команды /battle, чтобы выявить сильнейших.")
                    .parseMode(ParseMode.Markdown);
        }

        StringBuilder sb = new StringBuilder("🏆 *ТАБЛИЦА ЛИДЕРОВ БАТТЛОВ* 🏆\n\n");

        sb.append("🔥 *ТОП-5 МЕМОВ (по рейтингу ELO):*\n");
        if (topRatings.isEmpty()) {
            sb.append("_Рейтинги мемов еще не сформированы._\n");
        } else {
            for (int i = 0; i < topRatings.size(); i++) {
                MemeRating rating = topRatings.get(i);
                String desc = memeModerationRepository
                        .findById(rating.getMemeId())
                        .map(MemeModeration::getDescription)
                        .map(d -> d.length() > 30 ? d.substring(0, 27) + "..." : d)
                        .orElse("Без описания");

                sb.append(String.format(
                        "%d. ID: `%s` (%s) — *%d ELO* (В: %d, П: %d)\n",
                        i + 1, rating.getMemeId(), desc, rating.getEloRating(), rating.getWins(), rating.getLosses()));
            }
        }

        sb.append("\n👑 *ТОП-5 АВТОРОВ (по очкам побед):*\n");
        if (topUsers.isEmpty()) {
            sb.append("_Список авторов пуст._\n");
        } else {
            for (int i = 0; i < topUsers.size(); i++) {
                TelegramUser user = topUsers.get(i);
                String name = user.getUsername() != null ? ("@" + user.getUsername()) : user.getFirstName();
                sb.append(String.format(
                        "%d. %s — *%d очков* (%d побед)\n",
                        i + 1,
                        name,
                        user.getPoints() != null ? user.getPoints() : 0,
                        user.getBattleWins() != null ? user.getBattleWins() : 0));
            }
        }

        return new SendMessage(chatId, sb.toString()).parseMode(ParseMode.Markdown);
    }
}
