package ru.krsmon.bridge.external.telegram.bots;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.krsmon.bridge.external.zabbix.service.ZabbixService;
import ru.krsmon.bridge.external.zabbix.service.impl.ZabbixServiceImpl;
import java.util.List;

import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.LocalDate.now;
import static java.util.Arrays.stream;
import static java.util.Objects.nonNull;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "external.bots.bridgeNotifyBot.enabled", havingValue = "true")
@ConditionalOnBean(ZabbixServiceImpl.class)
public class BridgeNotificationBot extends TelegramLongPollingBot {
    protected final ZabbixService zabbixService;

    @Getter
    @Value("${external.bots.bridgeNotifyBot.botUsername}")
    protected String botUsername;

    @Getter
    @Value("${external.bots.bridgeNotifyBot.botToken}")
    protected String botToken;

    @Value("${external.bots.adminId}")
    protected Long adminId;

    @Override
    @SneakyThrows
    public void onUpdateReceived(Update update) {
        log.info(update.getMessage().getFrom().getId().toString());
        if (update.hasMessage() && update.getMessage().hasText() && adminId.equals(update.getMessage().getFrom().getId())) {
            log.info("NOTIFY: Received сmd '%s' from user '%s'"
                    .formatted(update.getMessage().getText(), update.getMessage().getFrom().getId()));

            final Message message = update.getMessage();
            if (message.getText().startsWith("/export")) {
                final List<String> groups = stream(message.getText().substring(8).split(",")).toList();
                var data = zabbixService.exportHosts(groups);
                if (nonNull(data)) {
                    var document = new SendDocument();
                    document.setCaption("Export date: %s".formatted(now()));
                    document.setChatId(message.getChatId());
                    document.setReplyToMessageId(message.getMessageId());
                    document.setProtectContent(true);
                    document.setDisableContentTypeDetection(true);
                    document.setDocument(new InputFile(IOUtils.toInputStream(data, UTF_8), "export-host.xml"));
                    executeAsync(document);
                } else {
                    sendMessage(message, "Fail to export, data is null.");
                }
            } else {
                executeAsync(new DeleteMessage(
                        valueOf(update.getMessage().getChatId()),
                        update.getMessage().getMessageId()));
            }
        }
    }

    @SneakyThrows
    private void sendMessage(@NonNull Message source, @NonNull String response) {
        var newMessage = new SendMessage();
        newMessage.setChatId(source.getChatId());
        newMessage.setReplyToMessageId(source.getMessageId());
        newMessage.setText(response);
        executeAsync(newMessage);
    }

}
