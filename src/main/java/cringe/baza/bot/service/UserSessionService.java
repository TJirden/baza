package cringe.baza.bot.service;

import cringe.baza.bot.model.UserState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserSessionService {

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, String> tempData = new ConcurrentHashMap<>();

    public UserState getUserState(Long chatId) {
        return userStates.getOrDefault(chatId, UserState.DEFAULT);
    }

    public void setUserState(Long chatId, UserState state) {
        if (state == UserState.DEFAULT) {
            userStates.remove(chatId);
            tempData.remove(chatId);
        } else {
            userStates.put(chatId, state);
        }
        log.debug("User {} state changed to {}", chatId, state);
    }

    public void setTempData(Long chatId, String data) {
        tempData.put(chatId, data);
    }

    public String getTempData(Long chatId) {
        return tempData.get(chatId);
    }

    public void clearTempData(Long chatId) {
        tempData.remove(chatId);
    }
}