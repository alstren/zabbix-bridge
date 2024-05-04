package ru.krsmon.bridge.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.expectit.Expect;
import net.sf.expectit.ExpectBuilder;
import org.apache.commons.net.telnet.TelnetClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.krsmon.bridge.external.zabbix.service.ZabbixService;
import ru.krsmon.bridge.model.*;
import ru.krsmon.bridge.service.AbstractRouterClient;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static net.sf.expectit.matcher.Matchers.contains;
import static org.apache.commons.net.SocketClient.NETASCII_EOL;
import static ru.krsmon.bridge.external.zabbix.model.ZabbixConstrains.MACRO_ROUTER_ID;
import static ru.krsmon.bridge.model.DeviceStatus.Status.OFFLINE;
import static ru.krsmon.bridge.model.DeviceStatus.Status.ONLINE;
import static ru.krsmon.bridge.model.DeviceType.ROUTER;
import static ru.krsmon.bridge.model.Policy.PING_OR_PORTS;
import static ru.krsmon.bridge.model.Policy.PORT_ONLY;
import static ru.krsmon.bridge.utils.RegexUtils.*;

@Slf4j
@Service("TELNET")
@RequiredArgsConstructor
public class TelnetRouterClientImpl extends AbstractRouterClient {
    protected final ZabbixService zabbixService;

    @Value("${server.tomcat.connection-timeout}")
    private Integer timeout;

    @NonNull
    @Override
    @SneakyThrows
    public SurveyResponse executeFullSurvey(@NonNull SurveyRequest request, @NonNull Brand brand) {
        final TelnetClient client = new TelnetClient();
        final StringBuilder logger = new StringBuilder();
        try {
            connectivityCheck(request);
            client.setDefaultTimeout(timeout * 1000);
            client.connect(request.getIp(), request.getService());
            if (client.isConnected()) {
                try (final Expect expect = createExpect(client, logger)) {
                    expect.withTimeout(10, SECONDS).expect(contains(LOGIN));
                    expect.sendLine(request.getLogin());
                    expect.withTimeout(10, SECONDS).expect(contains(PASS));
                    expect.sendLine(request.getPassword());
                    expect.withTimeout(10, SECONDS).expect(contains(brand.getInvite()));
                    log.info("TELNET: Connected to '%s:%s.'".formatted(request.getIp(), request.getService()));

                    Map<String, String> macrosNeedUpdate = new HashMap<>();
                    if (request.getZabbixId() == 0) {
                        request.setZabbixId(zabbixService.getHostId(request.getName()));
                        if (request.getZabbixId() == 0) {
                            log.warn("SHELL: Zabbix router ID not set.");
                        } else {
                            macrosNeedUpdate.put(MACRO_ROUTER_ID, valueOf(request.getZabbixId()));
                        }
                    }

                    final Map<String, String> arpMap =
                            getArpMap(executeRemoteCommand(expect, logger, brand.getArp(), brand.getInvite()));
                    log.info("TELNET: Received device list: '%s'.".formatted(arpMap.entrySet().toString()));

                    final Map<String, DeviceStatus> resultMap = request.devices().stream()
                            .filter(AbstractRouterClient::isNonDefaultDevice)
                            .peek(device -> device.setIp(arpMap.getOrDefault(device.getMac(), device.getIp())))
                            .map(device -> {
                                var result = fullCheck(device, expect, logger, brand, request.getIp());
                                if (ONLINE.getCode() == result.getValue().code())
                                    add2UpdateIfNeed(device, arpMap, macrosNeedUpdate);
                                return result;
                            })
                            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (firstVal, secondVal) -> firstVal));

                    if (request.getZabbixId() != 0 && !macrosNeedUpdate.isEmpty())
                        executorService.schedule(
                                () -> zabbixService.updateMacros(request.getZabbixId(), macrosNeedUpdate), 5, SECONDS);

                    return completeResults(resultMap, new StringBuilder(), arpMap, request.mac2key());
                }
            }
            return executeSurveyShort(request, true);
        } catch (Exception ex) {
            log.error("TELNET: %s: CRITICAL FAIL: '%s'".formatted(request.getName(), ex.getLocalizedMessage()));
            return executeSurveyShort(request, true);
        } finally {
            if (client.isConnected()) client.disconnect();
        }
    }

    @NonNull
    private Map.Entry<String, DeviceStatus> fullCheck(@NonNull Device device,
                                                      @NonNull Expect expect,
                                                      @NonNull StringBuilder logger,
                                                      @NonNull Brand brand,
                                                      @NonNull String hostIp) {
        final Set<Integer> openedPorts = scanPorts(hostIp, device.getPorts());
        log.info("TELNET: Opened ports on '%s' is '%s'".formatted(device.getType(), openedPorts));

        if (ROUTER.equals(device.getType())) {
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            openedPorts.isEmpty() ? OFFLINE.getCode() : ONLINE.getCode(),
                            ROUTER_CHECK_SUCCESS.formatted(openedPorts)));
        }

        try {
            final Map.Entry<Boolean, String> pingResult = DEFAULT_IP.equalsIgnoreCase(device.getIp())
                    ? Map.entry(false, IP_NOT_FOUND_IN_ARP)
                    : executePing(expect, logger, brand.getPing(device.getIp()), brand.getInvite());
            log.info("TELNET: Ping device '%s', result: '%s'.".formatted(device.getType(), pingResult));

            var isPingFail = NOT_RECOGNIZED.equalsIgnoreCase(pingResult.getValue());
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            isOnline(pingResult.getKey(), !openedPorts.isEmpty(),
                                    isPingFail ? PORT_ONLY : device.getPolicy()) ? ONLINE.getCode() : OFFLINE.getCode(),
                            FULL_CHECK_RESULT.formatted(pingResult.getValue(), openedPorts)));
        } catch (Exception ex) {
            log.warn("TELNET: Device check failed, message: '%s'".formatted(ex.getLocalizedMessage()));
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            isOnline(false, !openedPorts.isEmpty(), PING_OR_PORTS) ? ONLINE.getCode() : OFFLINE.getCode(),
                            FULL_CHECK_RESULT.formatted(ex.getLocalizedMessage(), openedPorts)));
        }
    }

    @NonNull
    private Map.Entry<Boolean, String> executePing(@NonNull Expect expect, @NonNull StringBuilder logger,
                                                   @NonNull String cmd, @NonNull String invite) throws IOException {
        var result = toPingResult(executeRemoteCommand(expect, logger, cmd, invite));
        // Repeated ping to prevent frequent false signals
        return result.getKey() ? result : toPingResult(executeRemoteCommand(expect, logger, cmd, invite));
    }

    @NonNull
    private String executeRemoteCommand(@NonNull Expect expect, @NonNull StringBuilder logger,
                                        @NonNull String cmd, @NonNull String invite) throws IOException {
        expect.sendLine(cmd);
        var uuid = UUID.randomUUID().toString();
        logger.append(uuid);
        expect.withTimeout(10, SECONDS).expect(contains(invite));
        return logger.substring(logger.toString().indexOf(uuid));
    }

    @NotNull
    @SneakyThrows
    private Expect createExpect(@NonNull TelnetClient client, @NonNull StringBuilder logger) {
        return new ExpectBuilder()
                .withOutput(client.getOutputStream())
                .withInputs(client.getInputStream())
                .withEchoOutput(logger)
                .withEchoInput(logger)
                .withLineSeparator(NETASCII_EOL)
                .withAutoFlushEcho(true)
                .withExceptionOnFailure()
                .withCharset(UTF_8)
                .build();
    }

}
