package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.domain.MemeModeration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramServiceImpl implements TelegramService {

    private final TelegramBot bot;

    @Override
    public void sendMessage(long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    @Override
    public void sendMessageWithMarkdown(long chatId, String text) {
        bot.execute(new SendMessage(chatId, text).parseMode(ParseMode.Markdown));
    }

    @Override
    public void sendPhoto(long chatId, String fileId, String caption) {
        bot.execute(new SendPhoto(chatId, fileId).caption(caption));
    }

    @Override
    public void sendPhotoWithMarkdown(long chatId, String fileId, String caption) {
        bot.execute(new SendPhoto(chatId, fileId).caption(caption).parseMode(ParseMode.Markdown));
    }

    @Override
    public void sendSwipeCard(long chatId, String fileId, String caption, String memeId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                        new InlineKeyboardButton("🔥 База").callbackData("swipe_vote:" + memeId + ":BASE"),
                        new InlineKeyboardButton("💩 Кринж").callbackData("swipe_vote:" + memeId + ":CRINGE"))
                .addRow(new InlineKeyboardButton("🛑 Выйти").callbackData("swipe_stop"));

        bot.execute(new SendPhoto(chatId, fileId)
                .caption(caption)
                .parseMode(ParseMode.Markdown)
                .replyMarkup(keyboard));
    }

    @Override
    public Integer sendBattleStart(
            long chatId,
            String fileIdA,
            String captionA,
            String fileIdB,
            String captionB,
            String voteText,
            long battleId) {
        bot.execute(new SendPhoto(chatId, fileIdA).caption(captionA).parseMode(ParseMode.Markdown));
        bot.execute(new SendPhoto(chatId, fileIdB).caption(captionB).parseMode(ParseMode.Markdown));

        InlineKeyboardMarkup keyboard = getBattleVoteKeyboard(battleId);
        var response = bot.execute(
                new SendMessage(chatId, voteText).parseMode(ParseMode.Markdown).replyMarkup(keyboard));

        if (response != null && response.isOk() && response.message() != null) {
            return response.message().messageId();
        }
        return null;
    }

    @Override
    public void editMessageText(long chatId, int messageId, String text) {
        bot.execute(new EditMessageText(chatId, messageId, text));
    }

    @Override
    public void editMessageTextWithMarkdown(long chatId, int messageId, String text) {
        bot.execute(new EditMessageText(chatId, messageId, text).parseMode(ParseMode.Markdown));
    }

    @Override
    public void editBattleVoteCard(long chatId, int messageId, String text, long battleId) {
        InlineKeyboardMarkup keyboard = getBattleVoteKeyboard(battleId);
        bot.execute(new EditMessageText(chatId, messageId, text)
                .parseMode(ParseMode.Markdown)
                .replyMarkup(keyboard));
    }

    @Override
    public void sendDuelMemeSelection(long userId, String text, long battleId, List<MemeModeration> userMemes) {
        InlineKeyboardButton[][] buttons = new InlineKeyboardButton[userMemes.size()][1];
        for (int i = 0; i < userMemes.size(); i++) {
            MemeModeration meme = userMemes.get(i);
            buttons[i][0] = new InlineKeyboardButton(String.format("Мем %d", i + 1))
                    .callbackData(String.format("duel_select:%d:%s", battleId, meme.getId()));
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(buttons);
        bot.execute(new SendMessage(userId, text).parseMode(ParseMode.Markdown).replyMarkup(keyboard));
    }

    private InlineKeyboardMarkup getBattleVoteKeyboard(long battleId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("👍 Вариант А").callbackData("vote:" + battleId + ":A"),
                new InlineKeyboardButton("👍 Вариант Б").callbackData("vote:" + battleId + ":B"));
    }
}
