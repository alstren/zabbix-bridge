package ru.krsmon.bridge.model;

import lombok.Data;
import org.springframework.lang.NonNull;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.of;
import static ru.krsmon.bridge.model.DeviceType.ROUTER;
import static ru.krsmon.bridge.model.Policy.PING_OR_PORTS;

@Data
public class Device {
    private String mac;
    private DeviceType type;
    private String ip;
    private int http;
    private int media;
    private int service;
    private String key;
    private String spec;

    public static Device router(@NonNull String ip, int http, int service) {
        final Device device = new Device();
        device.setIp(ip);
        device.setHttp(http);
        device.setService(service);
        device.setType(ROUTER);
        device.setKey(ROUTER.name());
        device.setMac("00:11:22:33:44:55");
        return device;
    }

    public Policy getPolicy() {
        return (isNull(spec) || !asList(spec.split(",")).contains("POR"))
                ? type.getPolicy()
                : PING_OR_PORTS;
    }

    public String getMac() {
        return mac.toUpperCase();
    }

    public Set<Integer> getPorts() {
        return of(http, media, service)
                .filter(port -> port != 0)
                .collect(toSet());
    }

}
