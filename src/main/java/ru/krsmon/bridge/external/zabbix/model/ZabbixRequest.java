package ru.krsmon.bridge.external.zabbix.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@Builder
@ToString
public class ZabbixRequest implements Serializable {
    @Builder.Default
    private String jsonrpc = "2.0";

    @Builder.Default
    private int id = 1;

    private String auth;
    private String method;
    private Object params;
}
