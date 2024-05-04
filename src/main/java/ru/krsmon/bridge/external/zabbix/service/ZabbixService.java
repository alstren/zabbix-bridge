package ru.krsmon.bridge.external.zabbix.service;

import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ZabbixService {

    /**
     * Get ID host by name
     *
     * @param hostName имя хоста
     * @return ID хост
     */
    int getHostId(@NonNull String hostName);

    /**
     * Update or create macros into zabbix
     *
     * @param hostId ID хоста
     * @param macros карта макрос/значение
     */
    void updateMacros(int hostId, @NonNull Map<String, String> macros);

    /**
     * Export hosts by group names
     *
     * @param groups group names
     * @return export data in xml format
     */
    String exportHosts(@NonNull List<String> groups);

    /**
     * Getting host macros by hostId
     *
     * @param hostId id of hosts
     * @return macros map
     */
    Map<String, Object> getHostMacros(int hostId);

    /**
     * Get group ids by group names
     *
     * @param groupNames names of group
     * @return collection of group ids
     */
    @NonNull
    Optional<List<String>> getHostIdsByGroupNames(@NonNull List<String> groupNames);
}
