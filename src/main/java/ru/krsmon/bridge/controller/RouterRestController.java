package ru.krsmon.bridge.controller;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.krsmon.bridge.model.Brand;
import ru.krsmon.bridge.model.Protocol;
import ru.krsmon.bridge.model.SurveyRequest;
import ru.krsmon.bridge.model.SurveyResponse;
import ru.krsmon.bridge.service.RouterClient;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static ru.krsmon.bridge.model.Brand.TPLINK;
import static ru.krsmon.bridge.service.AbstractRouterClient.executeSurveyShort;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/router")
@CrossOrigin(methods = POST, maxAge = 3600)
public class RouterRestController {
    protected final Map<String, RouterClient> clients;

    @PostMapping("/{brand}/{protocol}/survey")
    @Timed(value = "survey.timed", description = "Time to check router devices.")
    public ResponseEntity<SurveyResponse> survey(@PathVariable Brand brand,
                                                 @PathVariable Protocol protocol,
                                                 @RequestBody SurveyRequest request) {
        return !TPLINK.equals(brand) && clients.containsKey(protocol.name())
                ? ResponseEntity.ok(clients.get(protocol.name()).executeFullSurvey(request, brand))
                : ResponseEntity.ok(executeSurveyShort(request, false));
    }

}
