package ru.krsmon.bridge.external.zabbix.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.krsmon.bridge.external.zabbix.model.ZabbixRequest;
import ru.krsmon.bridge.external.zabbix.service.ZabbixService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static okhttp3.MediaType.get;
import static okhttp3.RequestBody.create;
import static ru.krsmon.bridge.external.zabbix.model.ZabbixConstrains.*;

@Slf4j
@Service
@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class ZabbixServiceImpl implements ZabbixService {
    protected final OkHttpClient client;
    protected final ObjectMapper mapper;

    @Value("${external.zabbix.url}")
    protected String zabbixUrl;

    @Value("${external.zabbix.token}")
    protected String apiToken;

    @Override
    @Timed(value = "zabbix.get.host.id.timed", description = "Time to getting zabbix host id.")
    public int getHostId(@NonNull String hostName) {
        int id = 0;
        try {
            log.info("ZABBIX: Get host ID by NAME '%s'...".formatted(hostName));
            var request = ZabbixRequest.builder()
                    .method(HOST_GET)
                    .auth(apiToken)
                    .params(Map.of(
                            OUTPUT, List.of(HOSTID),
                            FILTER, Map.of(NAME, hostName)
                    )).build();

            var response = apiRequest(request);
            var hosts = (List<Map<String, String>>) response.get(RESULT);

            if (hosts.isEmpty()) {
                log.warn("ZABBIX: Hosts lists is empty (not found).");
            }
            id = Integer.parseInt(hosts.get(0).get(HOSTID));
        } catch (NoClassDefFoundError | Exception exception) {
            log.warn("ZABBIX: Fail getting host, message: '%s'".formatted(exception.getLocalizedMessage()));
        }
        return id;
    }

    @Override
    @SneakyThrows
    @Timed(value = "zabbix.update.macros.timed", description = "Time to update zabbix macros.")
    public void updateMacros(int hostId, @NonNull Map<String, String> macros) {
        final List<Map<String, String>> hostMacros = (List<Map<String, String>>) getHostMacros(hostId).get(RESULT);
        final ArrayList<Map<String, String>> macros2Update = new ArrayList<>();
        final ArrayList<Map<String, String>> macros2Create = new ArrayList<>();

        for (var updMacro : macros.entrySet()) {
            var targetMap = hostMacros.stream()
                    .filter(ssMap -> ssMap.containsValue(updMacro.getKey()))
                    .findFirst();

            if (targetMap.isPresent()) {
                // UPDATE
                macros2Update.add(Map.of(
                        HOSTMACROID, targetMap.get().get(HOSTMACROID),
                        VALUE, updMacro.getValue()
                ));
            } else {
                // CREATE
                macros2Create.add(Map.of(
                        HOSTID, String.valueOf(hostId),
                        MACRO, updMacro.getKey(),
                        VALUE, updMacro.getValue()
                ));
            }
        }

        if (!macros2Update.isEmpty()) {
            try {
                log.info("ZABBIX: Update host macros by hostId '%s'...".formatted(hostId));
                var request = ZabbixRequest.builder()
                        .method(USERMACRO_UPDATE)
                        .auth(apiToken)
                        .params(macros2Update)
                        .build();
                apiRequest(request);
            } catch (NoClassDefFoundError | Exception exception) {
                log.warn("ZABBIX: Fail updating host macros, message: '%s'".formatted(exception.getLocalizedMessage()));
            }
        }

        if (!macros2Create.isEmpty()) {
            log.info("ZABBIX: Create host macros by hostId '%s'...".formatted(hostId));
            try {
                var request = ZabbixRequest.builder()
                        .method(USERMACRO_CREATE)
                        .auth(apiToken)
                        .params(macros2Create)
                        .build();
                apiRequest(request);
            } catch (NoClassDefFoundError | Exception exception) {
                log.warn("ZABBIX: Fail creating host macros, message: '%s'".formatted(exception.getLocalizedMessage()));
            }
        }
    }

    @Override
    @Timed(value = "zabbix.export.hosts.timed", description = "Time to export zabbix hosts.")
    public String exportHosts(@NonNull List<String> groups) {
        log.info("ZABBIX: Export hosts from groups '%s' to file".formatted(groups));
        var requestHostIds = new ArrayList<String>();
        try {
            getHostIdsByGroupNames(groups).ifPresent(requestHostIds::addAll);

            var request = ZabbixRequest.builder()
                    .method(CONFIGURATION_EXPORT)
                    .auth(apiToken)
                    .params(Map.of(
                            OPTIONS, Map.of(HOSTS, requestHostIds),
                            "format", "xml"
                    ))
                    .build();

            var response = apiRequest(request);
            log.info("ZABBIX: Successfully exported hosts from groups '%s'".formatted(groups));
            return (String) response.get(RESULT);
        } catch (Exception ex) {
            log.warn("ZABBIX: Fail export hosts by groups '%s', message: '%s'"
                    .formatted(groups, ex.getLocalizedMessage()));
            return null;
        }
    }

    @Override
    @Timed(value = "zabbix.get.host.macros.timed", description = "Time to getting zabbix host macros.")
    public Map<String, Object> getHostMacros(int hostId) {
        try {
            log.info("ZABBIX: Get host macros by hostId '%s'...".formatted(hostId));
            var request = ZabbixRequest.builder()
                    .method(USERMACRO_GET)
                    .auth(apiToken)
                    .params(Map.of(
                            OUTPUT, EXTEND,
                            HOSTIDS, String.valueOf(hostId)
                    )).build();

            var response = apiRequest(request);
            log.info("ZABBIX: Map macros from host: '%s'".formatted(response));
            return response;
        } catch (NoClassDefFoundError | Exception exception) {
            log.warn("ZABBIX: Fail getting host macros, message: '%s'".formatted(exception.getLocalizedMessage()));
            return Map.of();
        }
    }

    @NonNull
    @Override
    @Timed(value = "zabbix.get.host.ids.by.group.timed", description = "Time to getting zabbix host ids by groups.")
    public Optional<List<String>> getHostIdsByGroupNames(@NonNull List<String> groupNames) {
        try {
            log.info("ZABBIX: Get group and host ids by names...");
            var request = ZabbixRequest.builder()
                    .method(HOSTGROUP_GET)
                    .auth(apiToken)
                    .params(Map.of(
                            OUTPUT, GROUPID,
                            SELECT_HOSTS, HOSTID,
                            FILTER, Map.of(NAME, groupNames)
                    )).build();

            var response = apiRequest(request);
            log.info("ZABBIX: Successfully getting group ids by names.");
            return Optional.of(((List<Map<String, Object>>) response.get(RESULT)).stream()
                    .flatMap(this::toHostIds)
                    .toList());
        } catch (Exception ex) {
            log.warn("ZABBIX: Fail to getting group ids by names '%s', message '%s'"
                    .formatted(groupNames, ex.getLocalizedMessage()));
            return Optional.empty();
        }
    }

    @NotNull
    private Map<String, Object> apiRequest(@NonNull ZabbixRequest body) throws Exception {
        final Request request = new Request.Builder()
                .url(zabbixUrl)
                .post(create(mapper.writeValueAsString(body), get("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || isNull(response.body())) {
                log.warn("ZABBIX: Fail request, request '%s', response '%s'"
                        .formatted(body.toString(), isNull(response.body()) ? "null" : response.body().string()));
                return emptyMap();
            }

            Map<String, Object> result = mapper.readValue(response.body().string(), new TypeReference<>() {});
            if (result.containsKey(ERROR)) {
                var error = (Map<String, Object>) result.get(ERROR);
                var message = Optional.of(error.get(MESSAGE)).orElse("n/a");
                var data = Optional.of(error.get(DATA)).orElse("n/a");
                log.warn("ZABBIX: Fail validation, message: '%s', data: '%s'".formatted(message, data));
                return emptyMap();
            }

            return result;
        }
    }

    private Stream<String> toHostIds(@NotNull Map<String, Object> groups) {
        return ((List<Map<String, String>>) groups.get(HOSTS)).stream()
                .flatMap(hosts -> hosts.values().stream());
    }

}
