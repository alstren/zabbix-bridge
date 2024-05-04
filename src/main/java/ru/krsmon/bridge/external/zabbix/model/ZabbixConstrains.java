package ru.krsmon.bridge.external.zabbix.model;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ZabbixConstrains {
    public static final String HOST_GET = "host.get";
    public static final String HOSTGROUP_GET = "hostgroup.get";
    public static final String USERMACRO_GET = "usermacro.get";
    public static final String USERMACRO_UPDATE = "usermacro.update";
    public static final String USERMACRO_CREATE = "usermacro.create";
    public static final String CONFIGURATION_EXPORT = "configuration.export";
    public static final String DATA = "data";
    public static final String NAME = "name";
    public static final String MACRO = "macro";
    public static final String VALUE = "value";
    public static final String ERROR = "error";
    public static final String HOSTS = "hosts";
    public static final String RESULT = "result";
    public static final String OUTPUT = "output";
    public static final String FILTER = "filter";
    public static final String GROUPID = "groupid";
    public static final String HOSTID = "hostid";
    public static final String EXTEND = "extend";
    public static final String MESSAGE = "message";
    public static final String HOSTIDS = "hostids";
    public static final String OPTIONS = "options";
    public static final String MACROS_LIP = "{$%s.IP}";
    public static final String HOSTMACROID = "hostmacroid";
    public static final String SELECT_HOSTS = "selectHosts";
    public static final String MACRO_ROUTER_ID = "{$ROUTER.ID}";
}
