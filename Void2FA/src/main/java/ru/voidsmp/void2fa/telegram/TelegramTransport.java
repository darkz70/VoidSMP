package ru.voidsmp.void2fa.telegram;

/** Telegram output boundary. Authentication decisions never belong to a transport. */
public interface TelegramTransport {
    boolean isReady();
    String getBotUsername();
    void sendMessage(long chatId, String text);
    void sendAuthRequest(String authId, long chatId, String text, String approveLabel, String denyLabel);
    void editAuthRequest(String authId, long chatId, String text);
    void answerCallbackQuery(String callbackId, String text);
    void stop();
}
