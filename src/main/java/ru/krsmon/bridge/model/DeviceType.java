package ru.krsmon.bridge.model;

import lombok.Getter;

@Getter
public enum DeviceType {
    DVR(Policy.PING_AND_PORTS),
    KEEPER(Policy.PING_OR_PORTS),
    KKT(Policy.PING_OR_PORTS),
    TERMINAL(Policy.PING_OR_PORTS),
    HOTSPOT(Policy.PING_OR_PORTS),
    ROUTER(Policy.PORT_ONLY)
    ;

    private final Policy policy;

    DeviceType(Policy policy) {
        this.policy = policy;
    }
}
