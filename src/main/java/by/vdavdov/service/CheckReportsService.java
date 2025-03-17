package by.vdavdov.service;

import by.vdavdov.model.*;
import by.vdavdov.utils.YamlUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class CheckReportsService {
    private static final Logger log = LogManager.getLogger(CheckReportsService.class);
    private final AuthService authService = new AuthService();
    private final CancelService cancelService = new CancelService();
    private final LastRunService lastRunService = new LastRunService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void getRejectedReports() throws Exception {
        Instant lastRun = lastRunService.getLastRunTime();
        Instant currentRun = Instant.now();

        int currentPage = 0;
        boolean hasMorePages = true;
        boolean hasErrors = false;

        while (hasMorePages && !hasErrors) {
            String url = YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports?"
                    + "query=status%3D%3DREJECTED%3Bupdated%3E%3D"
                    + URLEncoder.encode(lastRun.toString(), StandardCharsets.UTF_8)
                    + "&page=" + currentPage
                    + "&size=50";

            HttpResponse<String> response = sendGetRequest(url);
            if (response.statusCode() != 200) {
                log.error("Ошибка при получении страницы {}: {} {}", currentPage, response.statusCode(), response.body());
                hasErrors = true;
                continue;
            }

            try {
                ReportsResponse reportsResponse = parseResponse(response.body());
                processPage(reportsResponse.getContent());
                hasMorePages = !reportsResponse.isLast();
                currentPage++;
            } catch (IOException e) {
                log.error("Ошибка парсинга ответа", e);
                hasErrors = true;
            }
        }

        if (!hasErrors) {
            lastRunService.saveLastRunTime(currentRun);
            log.info("Обработка завершена. Следующий запуск через {} минут", YamlUtil.scheduleInterval);
        }
    }

    private void processPage(List<ContentItem> items) {
        items.forEach(item -> {
            try {
                if (item.getRejectionReason().contains("Повторное нанесение")) {
                    processPartialDuplicate(item.getId(), item.getOrderNumber());
                } else if (item.getRejectionReason().contains("Дубликат кода")) {
                    processFullDuplicate(item.getId());
                }
            } catch (Exception e) {
                log.error("Ошибка обработки отчета {}", item.getId(), e);
            }
        });
    }

    private void processFullDuplicate(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + reportId
        );

        if (response.statusCode() == 200) {
            JsonNode utils = objectMapper.readTree(response.body());
            if (utils.size() < 2) {
                sentToCancel(reportId);
                log.info("Отчет {} отменен", reportId);
            } else {
                log.info("Отчет {} содержит {} утилей, требуется ручная обработка", reportId, utils.size());
            }
        }
    }

    private void processPartialDuplicate(String reportId, String orderNumber) {
        try {
            log.info("[{}] Начало обработки частичного дубля", reportId);

            // 1. Получение кодов отчета
            List<CodeInfo> codes = getReportCodes(reportId);
            log.debug("[{}] Получено кодов: {}", reportId, codes.size());

            if (codes.isEmpty()) {
                log.warn("[{}] Нет кодов для обработки", reportId);
                return;
            }

            // 2. Получение ID утиля
            String utilId = getUtilIdForReport(reportId);
            if (utilId == null) {
                log.warn("[{}] Утиль не найден", reportId);
                return;
            }

            // 3. Получение последнего responsePath
            String responsePath = getLatestResponsePath(utilId);
            if (responsePath == null) {
                log.warn("[{}] Не найден responsePath", reportId);
                return;
            }

            // 4. Получение и обработка контента
            String responseContent = getResponseContent(responsePath);
            if (responseContent.isEmpty()) {
                log.warn("[{}] Пустой контент ответа", reportId);
                return;
            }

            // 5. Поиск кодов для удаления
            List<String> badCodes = processContent(responseContent, codes);
            log.info("[{}] Найдено кодов для удаления: {}", reportId, badCodes.size());

            // 6. Цикл удаления кодов
            while (!badCodes.isEmpty()) {
                log.debug("[{}] Попытка удаления {} кодов", reportId, badCodes.size());
                deleteCodesFromReport(reportId, badCodes);

                // Обновляем список кодов после удаления
                codes = getReportCodes(reportId);
                badCodes = processContent(responseContent, codes);
            }

            // 7. Финализация обработки
            acceptReport(utilId);
            recalculateOrder(reportId, orderNumber);
            log.info("[{}] Успешно обработан", reportId);

        } catch (Exception e) {
            log.error("[{}] Критическая ошибка обработки", reportId, e);
        }
    }

    private List<CodeInfo> getReportCodes(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId + "/codes?page=0&size=1000"
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        return objectMapper.readValue(
                objectMapper.readTree(response.body()).get("content").traverse(),
                new TypeReference<List<CodeInfo>>(){}
        );
    }

    private String getUtilIdForReport(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + reportId
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        JsonNode utils = objectMapper.readTree(response.body());
        if (utils.size() != 1) {
            log.info("[{}] Найдено {} утилей", reportId, utils.size());
            return null;
        }
        return utils.get(0).get("id").asText();
    }

    private String getLatestResponsePath(String utilId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + utilId + "/attempts?page=0&size=100"
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        AttemptsResponse attemptsResponse = objectMapper.readValue(response.body(), AttemptsResponse.class);
        return attemptsResponse.getContent().stream()
                .max(Comparator.comparing(Attempt::getCreated))
                .map(Attempt::getResponsePath)
                .orElse(null);
    }

    private String getResponseContent(String path) throws Exception {
        log.info("Запрос контента по пути: {}", path);

        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/regulator-ru-adapter/api/utilization-reports/attempts/content?path="
                        + URLEncoder.encode(path, StandardCharsets.UTF_8)
        );

        if (response.statusCode() != 200) {
            log.error("Ошибка получения контента: {}", response.statusCode());
            return "";
        }

        String rawResponse = response.body();
        JsonNode content = objectMapper.readTree(rawResponse);
        rawResponse = content.get("content").asText();

        log.debug("Raw response: {}", rawResponse);

        rawResponse = rawResponse.replace("\\n", "\n");
        log.info("JSJSSJ{}", rawResponse);
        String[] parts = rawResponse.split("\\n\\n", 2);

        if (parts.length < 2) {
            log.error("Не удалось разделить заголовки и тело. Ответ: {}", rawResponse);
            return "";
        }

        String jsonBody = parts[1].trim();
        log.debug("Извлеченное тело: {}", jsonBody);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonBody);
            JsonNode contentNode = rootNode.get("content");

            if (contentNode == null || !contentNode.isTextual()) {
                log.error("Некорректный формат поля content");
                return "";
            }

            String innerJson = contentNode.asText()
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            objectMapper.readTree(innerJson);
            return innerJson;

        } catch (Exception e) {
            log.error("Ошибка парсинга вложенного JSON. Тело: {}", jsonBody, e);
            return "";
        }
    }

    private List<String> processContent(String content, List<CodeInfo> dbCodes) {
        try {
            if (content == null || content.isEmpty()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(content);
            JsonNode cisList = root.get("cisList");

            if (cisList == null || !cisList.isArray()) {
                log.error("Отсутствует или неверный формат cisList");
                return Collections.emptyList();
            }

            Map<String, String> codeMap = dbCodes.stream()
                    .collect(Collectors.toMap(
                            c -> c.getCode().split("\u001d")[0], // Разделитель GS
                            CodeInfo::getCode
                    ));

            List<String> badCodes = new ArrayList<>();

            for (JsonNode cisEntry : cisList) {
                JsonNode descriptionNode = cisEntry.get("description");
                if (descriptionNode != null &&
                        descriptionNode.isTextual() &&
                        "Повторное нанесение кода".equals(descriptionNode.asText())) {

                    JsonNode cisNode = cisEntry.get("cis");
                    if (cisNode == null || !cisNode.isTextual()) {
                        continue;
                    }

                    String cis = cisNode.asText();
                    String codeKey = cis.split("\u001d")[0];

                    if (codeMap.containsKey(codeKey)) {
                        String codeToDelete = codeMap.get(codeKey)
                                .replace(" ", "\u001d")
                                .replace("\"", "\\\"");
                        badCodes.add(codeToDelete);
                    }
                }
            }
            return badCodes;
        } catch (IOException e) {
            log.error("Ошибка парсинга контента: {}", content, e);
            return Collections.emptyList();
        }
    }

    private void deleteCodesFromReport(String reportId, List<String> codes) throws Exception {
        if (codes.isEmpty()) {
            log.warn("[{}] Пустой список кодов для удаления", reportId);
            return;
        }

        String jsonBody = objectMapper.writeValueAsString(codes);
        log.debug("[{}] Тело запроса DELETE: {}", reportId, jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId + "/codes"))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        log.debug("[{}] Ответ DELETE: {} {}", reportId, response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка удаления кодов: " + response.body());
        }
    }

    private void acceptReport(String utilId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/code-usage-processor/api/code-usage-reports/accept-reports"))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[\"" + utilId + "\"]"))
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка принятия отчета: " + response.statusCode());
        }
    }

    private void recalculateOrder(String reportId, String orderNumber) throws Exception {
        if (orderNumber == null || orderNumber.isEmpty()) {
            orderNumber = getOrderNumber(reportId);
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new RuntimeException("Не удалось определить номер заказа");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/api/manufacturing-order-process/" + orderNumber + "/recalculate"))
                .header("Authorization", "Bearer " + authService.getToken())
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка пересчета счетчиков: " + response.statusCode());
        }
    }

    private String getOrderNumber(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка получения данных отчета: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode orderNumberNode = root.get("orderId");

        if (orderNumberNode == null || orderNumberNode.isNull()) {
            throw new RuntimeException("Поле orderId отсутствует в ответе");
        }

        return orderNumberNode.asText();
    }

    private HttpResponse<String> sendGetRequest(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("Authorization", "Bearer " + authService.getToken())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private ReportsResponse parseResponse(String json) throws IOException {
        return objectMapper.readValue(json, ReportsResponse.class);
    }

    public void sentToCancel(String reportId) throws Exception {
        cancelService.cancel(reportId);
    }
}