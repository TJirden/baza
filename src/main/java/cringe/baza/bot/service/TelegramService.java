package cringe.baza.bot.service;

import cringe.baza.domain.MemeModeration;
import java.util.List;

public interface TelegramService {
    void sendMessage(long chatId, String text);

    void sendMessageWithMarkdown(long chatId, String text);

    void sendPhoto(long chatId, String fileId, String caption);

    void sendPhotoWithMarkdown(long chatId, String fileId, String caption);

    void sendSwipeCard(long chatId, String fileId, String caption, String memeId);

    Integer sendBattleStart(
            long chatId,
            String fileIdA,
            String captionA,
            String fileIdB,
            String captionB,
            String voteText,
            long battleId);

    void editMessageText(long chatId, int messageId, String text);

    void editMessageTextWithMarkdown(long chatId, int messageId, String text);

    void editBattleVoteCard(long chatId, int messageId, String text, long battleId);

    void sendDuelMemeSelection(long userId, String text, long battleId, List<MemeModeration> userMemes);
}
