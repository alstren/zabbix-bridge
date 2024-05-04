package ru.krsmon.bridge.model;

import lombok.Getter;

public record DeviceStatus(
        int code,
        String message) {

    @Getter
    public enum Status {
        ONLINE(1),  // Device online
        OFFLINE(2), // Device offline
        NAN(3),     // Service general error
        ERROR(4);   // Ping error

        private final int code;

        Status(int code) {
            this.code = code;
        }
    }

}
