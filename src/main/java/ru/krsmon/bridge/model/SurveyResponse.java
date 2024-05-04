package ru.krsmon.bridge.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.NonNull;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

@Getter
@Setter
@Builder
public class SurveyResponse {
    private int code;
    private String message;
    private Map<String, String> discovery;
    private Map<String, DeviceStatus> result;
    private String checkedAt;

    public static Map<String, DeviceStatus> errorResult(@NonNull DeviceStatus.Status status) {
        return Stream.of("ROUTER", "DVR1", "DVR2", "CAM1", "CAM2", "KEEPER1", "KEEPER2", "HOTSPOT1", "TERMINAL1", "TERMINAL2", "KKT1", "KKT2")
                .map(key -> Map.entry(
                        key,
                        new DeviceStatus(status.getCode(), "n/a")))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (firstVal, secondVal) -> firstVal));
    }

}
