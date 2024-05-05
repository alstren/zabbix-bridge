package ru.krsmon.bridge.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.NonNull;
import ru.krsmon.bridge.model.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static ru.krsmon.bridge.external.zabbix.model.ZabbixConstrains.MACROS_LIP;
import static ru.krsmon.bridge.model.DeviceStatus.Status.OFFLINE;
import static ru.krsmon.bridge.model.DeviceStatus.Status.ONLINE;
import static ru.krsmon.bridge.model.DeviceType.ROUTER;
import static ru.krsmon.bridge.model.SurveyResponse.errorResult;

@Slf4j
public abstract class AbstractRouterClient implements RouterClient {
    protected static final String LOGIN = "ogin";
    protected static final String PASS = "assword";
    protected static final String DEFAULT_IP = "0.0.0.0";
    protected static final String IP_NOT_FOUND_IN_ARP = "IP not found in ARP";
    protected static final String FULL_CHECK_RESULT = "Ping: %s, available ports: %s";
    protected static final String SHORT_CHECK_RESULT = "No ping, available ports: %s";
    protected static final String ROUTER_CHECK_SUCCESS = "Connected, available ports: %s";
    protected static final String ROUTER_CHECK_SIMPLE = "Without connect, available ports: %s";
    protected static final String ROUTER_CHECK_FAIL = "Fail connect, available ports: %s";
    protected static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    protected final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    static protected SurveyResponse completeResults(@NonNull Map<String, DeviceStatus> resultMap,
                                                    @NonNull StringBuilder errors,
                                                    @NonNull Map<String, String> arpMap,
                                                    @NonNull Map<String, String> mac2Key) {
        final boolean isSuccess =  resultMap.values().stream().map(DeviceStatus::code).anyMatch(code -> code == 1);
        mac2Key.forEach((key, value) -> { if (arpMap.containsKey(key)) arpMap.put(value, arpMap.remove(key)); });
        return SurveyResponse.builder()
                .code(isSuccess ? errors.isEmpty() ? 200 : 201 : 503)
                .message(isSuccess ? errors.isEmpty() ? "Success" : errors.toString() : "HOST OFFLINE")
                .result(isSuccess ? resultMap : errorResult(OFFLINE, resultMap))
                .discovery(arpMap)
                .checkedAt(formatter.format(now()))
                .build();
    }

    public static boolean isOnline(boolean isSuccessPing, boolean hasOpenedPorts, @NonNull Policy policy) {
        switch (policy) {
            case PING_AND_PORTS -> {
                return isSuccessPing && hasOpenedPorts;
            }
            case PING_OR_PORTS -> {
                return isSuccessPing || hasOpenedPorts;
            }
            case PORT_ONLY -> {
                return hasOpenedPorts;
            }
            default -> {
                return isSuccessPing;
            }
        }
    }

    static public boolean isNonDefaultDevice(@NonNull Device device) {
        return !"00:00:00:00:00:00".equalsIgnoreCase(device.getMac());
    }

    @NotNull
    static protected Set<Integer> scanPorts(@NonNull String host, @NonNull Set<Integer> ports) {
        final Set<Integer> result = new HashSet<>();
        for (var port : ports) {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 6_000);
                if (socket.isConnected()) result.add(port);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    protected static void connectivityCheck(@NotNull SurveyRequest request) throws RuntimeException {
        if (scanPorts(request.getIp(), Set.of(request.getService())).isEmpty())
            throw new RuntimeException("Service port not available, run short scenery.");
    }

    @NonNull
    public static SurveyResponse executeSurveyShort(@NonNull SurveyRequest request, boolean hasError) {
        final Map<String, DeviceStatus> resultMap = request.devices().stream()
                .filter(AbstractRouterClient::isNonDefaultDevice)
                .map(device -> {
                    final Set<Integer> openedPorts = scanPorts(request.getIp(), device.getPorts());
                    return ROUTER.equals(device.getType())
                            ? Map.entry(device.getKey(),
                            new DeviceStatus(openedPorts.isEmpty() ? OFFLINE.getCode() : ONLINE.getCode(),
                                    hasError
                                            ? ROUTER_CHECK_FAIL.formatted(openedPorts)
                                            : ROUTER_CHECK_SIMPLE.formatted(openedPorts)))
                            : Map.entry(device.getKey(),
                            new DeviceStatus(openedPorts.isEmpty() ? OFFLINE.getCode() : ONLINE.getCode(),
                                    SHORT_CHECK_RESULT.formatted(openedPorts)));
                })
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (firstVal, secondVal) -> firstVal));

        final StringBuilder builder = new StringBuilder();
        if (hasError) builder.append("Connection fail.");
        return AbstractRouterClient.completeResults(resultMap, builder , emptyMap(), request.mac2key());
    }

    @SneakyThrows
    protected void add2UpdateIfNeed(@NonNull Device device, @NonNull Map<String, String> arpMap,
                                    @NonNull Map<String, String> macrosNeedUpdate) {
        if (arpMap.containsKey(device.getMac()) && !device.getIp().equalsIgnoreCase(arpMap.get(device.getMac()))) {
            log.info("IP '%s' will change to '%s'".formatted(device.getIp(), arpMap.get(device.getMac())));
            macrosNeedUpdate.put(
                    MACROS_LIP.formatted(device.getKey().toUpperCase()),
                    arpMap.get(device.getMac()));
        }
    }

}
