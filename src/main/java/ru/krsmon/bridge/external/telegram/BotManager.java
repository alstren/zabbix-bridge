package ru.krsmon.bridge.external.telegram;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Component
public class BotManager implements HealthIndicator {
    protected final Map<String, BotSession> botSessionMap = new LinkedHashMap<>();
    protected final Set<TelegramLongPollingBot> bots;
    protected final TelegramBotsApi telegramBotsApi;

    @SneakyThrows
    public BotManager(Set<TelegramLongPollingBot> bots) {
        this.bots = bots;
        this.telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        try {
            for (var bot : bots) {
                botSessionMap.put(bot.getBotUsername(), telegramBotsApi.registerBot(bot));
                log.info("TELEGRAM API: Bot '%s' is %s.".formatted(bot.getBotUsername(),
                        botSessionMap.get(bot.getBotUsername()).isRunning() ? "running" : "fail."));
            }
        } catch (Exception ex) {
            log.error("TELEGRAM API: %S".formatted(ex.getLocalizedMessage()));
        }
    }

    @Override
    public Health health() {
        return (botSessionMap.values().stream().anyMatch(session -> !session.isRunning()) ? Health.down() : Health.up())
                .withDetails(botSessionMap.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(), entry.getValue().isRunning() ? "running" : "fail"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (firstVal, secondVal) -> firstVal)))
                .build();
    }

}
