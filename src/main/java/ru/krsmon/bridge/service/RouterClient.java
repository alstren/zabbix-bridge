package ru.krsmon.bridge.service;

import org.springframework.lang.NonNull;
import ru.krsmon.bridge.model.Brand;
import ru.krsmon.bridge.model.SurveyRequest;
import ru.krsmon.bridge.model.SurveyResponse;

public interface RouterClient {

    /**
     * Запуск проверки оборудования
     *
     * @param request креды точки и перечень оборудования
     * @param brand бренд роутера
     * @return результаты проверок
     */
    @NonNull
    SurveyResponse executeFullSurvey(@NonNull SurveyRequest request, @NonNull Brand brand);

}
