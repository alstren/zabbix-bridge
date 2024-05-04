package ru.krsmon.bridge.model;

import lombok.Getter;
import lombok.Setter;
import ru.krsmon.bridge.service.AbstractRouterClient;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.of;
import static ru.krsmon.bridge.model.Device.router;

@Getter
@Setter
public class SurveyRequest {
    private int zabbixId;
    private String ip;
    private String name;
    private String login;
    private String password;
    private int http;
    private int service;
    private Set<Device> devices;

    public Set<Integer> ports() {
        return of(http, service)
                .filter(port -> port != 0)
                .collect(toSet());
    }

    public Set<Device> devices() {
        this.devices.add(router(ip, http, service));
        return this.devices;
    }

    public Map<String, String> mac2key() {
        return devices.stream()
                .filter(AbstractRouterClient::isNonDefaultDevice)
                .map(device -> entry(device.getMac(), device.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (firstVal, secondVal) -> firstVal));
    }


}
