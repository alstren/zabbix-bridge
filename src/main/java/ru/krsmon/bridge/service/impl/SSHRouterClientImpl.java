package ru.krsmon.bridge.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.session.ClientSession;
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

import static java.lang.String.valueOf;
import static java.lang.System.err;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.apache.sshd.client.SshClient.setUpDefaultSimpleClient;
import static ru.krsmon.bridge.external.zabbix.model.ZabbixConstrains.MACRO_ROUTER_ID;
import static ru.krsmon.bridge.model.DeviceStatus.Status.OFFLINE;
import static ru.krsmon.bridge.model.DeviceStatus.Status.ONLINE;
import static ru.krsmon.bridge.model.DeviceType.ROUTER;
import static ru.krsmon.bridge.model.Policy.PING_OR_PORTS;
import static ru.krsmon.bridge.model.Policy.PORT_ONLY;
import static ru.krsmon.bridge.utils.RegexUtils.*;

@Slf4j
@Service("SSH")
@RequiredArgsConstructor
public class SSHRouterClientImpl extends AbstractRouterClient {
    protected final ZabbixService zabbixService;

    @Value("${server.tomcat.connection-timeout}")
    private Integer timeout;

    @NonNull
    @Override
    public SurveyResponse executeFullSurvey(@NonNull SurveyRequest request, @NonNull Brand brand) {
        try (var client = setUpDefaultSimpleClient()) {
            connectivityCheck(request);
            client.setConnectTimeout(timeout * 1000);
            client.setAuthenticationTimeout(timeout * 100);

            final ClientSession session = client.sessionLogin(request.getIp(), request.getService(), request.getLogin(), request.getPassword());
            if (session.isAuthenticated() && session.isOpen()) {
                log.info("SSH: Connected to '%s:%s.'".formatted(request.getIp(), request.getService()));

                Map<String, String> macrosNeedUpdate = new HashMap<>();
                if (request.getZabbixId() == 0) {
                    request.setZabbixId(zabbixService.getHostId(request.getName()));
                    if (request.getZabbixId() == 0) {
                        log.warn("SHELL: Zabbix router ID not set.");
                    } else {
                        macrosNeedUpdate.put(MACRO_ROUTER_ID, valueOf(request.getZabbixId()));
                    }
                }

                final Map<String, String> arpMap = getArpMap(session.executeRemoteCommand(brand.getArp(), err, UTF_8));
                log.info("SSH: Received device list: '%s'.".formatted(arpMap.entrySet().toString()));

                final Map<String, DeviceStatus> resultMap = request.devices().stream()
                        .filter(AbstractRouterClient::isNonDefaultDevice)
                        .peek(device -> device.setIp(arpMap.getOrDefault(device.getMac(), device.getIp())))
                        .map(device -> {
                            var result = fullCheck(device, session, brand, request.getIp());
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
            return executeSurveyShort(request, true);
        } catch (Exception ex) {
            log.error("SSH: %s CRITICAL FAIL: '%s'".formatted(request.getName(), ex.getLocalizedMessage()));
            return executeSurveyShort(request, true);
        }
    }

    @NonNull
    private Map.Entry<String, DeviceStatus> fullCheck(@NonNull Device device,
                                                      @NonNull ClientSession session,
                                                      @NonNull Brand brand,
                                                      @NonNull String hostIp) {
        final Set<Integer> openedPorts = scanPorts(hostIp, device.getPorts());
        log.info("SSH: Opened ports on '%s' is '%s'".formatted(device.getType(), openedPorts));

        if (ROUTER.equals(device.getType())) {
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            openedPorts.isEmpty() ? OFFLINE.getCode() : ONLINE.getCode(),
                            ROUTER_CHECK_SUCCESS.formatted(openedPorts)));
        }

        try {
            final Map.Entry<Boolean, String> pingResult = DEFAULT_IP.equalsIgnoreCase(device.getIp())
                    ? Map.entry(false, IP_NOT_FOUND_IN_ARP)
                    : executePing(session, brand.getPing(device.getIp()));
            log.info("SSH: Ping device '%s', result: '%s'.".formatted(device.getType(), pingResult));

            var isPingFail = NOT_RECOGNIZED.equalsIgnoreCase(pingResult.getValue());
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            isOnline(pingResult.getKey(), !openedPorts.isEmpty(),
                                    isPingFail ? PORT_ONLY : device.getPolicy()) ? ONLINE.getCode() : OFFLINE.getCode(),
                            FULL_CHECK_RESULT.formatted(pingResult.getValue(), openedPorts)));
        } catch (Exception ex) {
            log.warn("SSH: Device check failed, message: '%s'".formatted(ex.getLocalizedMessage()));
            return Map.entry(device.getKey(),
                    new DeviceStatus(
                            isOnline(false, !openedPorts.isEmpty(), PING_OR_PORTS) ? ONLINE.getCode() : OFFLINE.getCode(),
                            FULL_CHECK_RESULT.formatted(ex.getLocalizedMessage(), openedPorts)));
        }
    }

    @NonNull
    private Map.Entry<Boolean, String> executePing(@NonNull ClientSession session, @NonNull String cmd) throws IOException {
        var result = toPingResult(session.executeRemoteCommand(cmd, err, UTF_8));
        // Repeated ping to prevent frequent false signals
        return result.getKey() ? result : toPingResult(session.executeRemoteCommand(cmd, err, UTF_8));
    }

}
