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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CheckReportsService {
    private static final Logger log = LogManager.getLogger(CheckReportsService.class);
    private final AuthService authService = new AuthService();
    private final CancelService cancelService = new CancelService();
    private final LastRunService lastRunService = new LastRunService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern CIS_PATTERN = Pattern.compile("\\{\\\\\"cis\\\\\":(.+?)}");

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

    private void processPage(List<ContentItem> items) throws Exception {
        Map<String, String> reports = new HashMap<>();
        for (ContentItem item : items) {
            reports.put(item.getId(), item.getRejectionReason());
        }
        checkUtils(reports);
    }

    private void checkUtils(Map<String, String> rejectedReports) throws Exception {
        for (Map.Entry<String, String> entry : rejectedReports.entrySet()) {
            String reportId = entry.getKey();
            String rejectionReason = entry.getValue();

            if (rejectionReason.contains("причина отклонения: Повторное нанесение кода.")) {
                processPartialDuplicate(reportId);
            } else if (rejectionReason.contains("Дубликат кода")) {
                processFullDuplicate(reportId);
            }
        }
    }

    private void processFullDuplicate(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + reportId
        );

        if (response.statusCode() == 200) {
            JsonNode utils = objectMapper.readTree(response.body());
            if (utils.size() < 2) {
                sentToCancel(reportId);
            } else {
                log.info("Отчет {} содержит {} утилей, требуется ручная обработка", reportId, utils.size());
            }
        }
    }

    private void processPartialDuplicate(String reportId) throws Exception {
        try {
            List<CodeInfo> codes = getReportCodes(reportId);
            if (codes.isEmpty()) {
                log.info("Нет кодов для отчета {}", reportId);
                return;
            }

            String utilId = getUtilIdForReport(reportId);
            if (utilId == null) return;

            String responsePath = getLatestResponsePath(utilId);
            if (responsePath == null) return;

            String responseContent = getResponseContent(responsePath);
            List<String> badCodes = processContent(responseContent, codes);

            while (!badCodes.isEmpty()) {
                deleteCodesFromReport(reportId, badCodes);
                codes = getReportCodes(reportId);
                badCodes = processContent(responseContent, codes);
            }

            acceptReport(utilId);
            recalculateOrder(reportId);
        } catch (Exception e) {
            log.error("Ошибка обработки частичного дубля для отчета {}", reportId, e);
        }
    }

    private List<CodeInfo> getReportCodes(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId + "/codes?page=0&size=1000"
        );
        return objectMapper.readValue(
                objectMapper.readTree(response.body()).get("content").traverse(),
                new TypeReference<List<CodeInfo>>(){}
        );
    }

    private String getUtilIdForReport(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + reportId
        );
        JsonNode utils = objectMapper.readTree(response.body());
        if (utils.size() != 1) {
            log.info("Найдено {} утилей для отчета {}, пропускаем", utils.size(), reportId);
            return null;
        }
        return utils.get(0).get("id").asText();
    }

    private String getLatestResponsePath(String utilId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/api/utilization-reports/" + utilId + "/attempts?page=0&size=100"
        );

        AttemptsResponse attemptsResponse = objectMapper.readValue(response.body(), AttemptsResponse.class);
        List<Attempt> attempts = attemptsResponse.getContent();

        return attempts.stream()
                .max(Comparator.comparing(Attempt::getCreated))
                .map(Attempt::getResponsePath)
                .orElse(null);
    }

    private String getResponseContent(String path) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/regulator-ru-adapter/api/utilization-reports/attempts/content?path="
                        + URLEncoder.encode(path, StandardCharsets.UTF_8)
        );
        return objectMapper.readTree(response.body()).get("content").asText();
    }

    private List<String> processContent(String content, List<CodeInfo> dbCodes) {
        Matcher matcher = CIS_PATTERN.matcher(content);
        List<String> badCodes = new ArrayList<>();

        while (matcher.find()) {
            String cisEntry = matcher.group(1);
            if (!cisEntry.contains("\\\"code\\\":0")) {
                String code = cisEntry.split("\\\\\",\\\\\"code\\\\\":")[0]
                        .substring(2)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                badCodes.add(code);
            }
        }

        Map<String, String> codeMap = dbCodes.stream()
                .collect(Collectors.toMap(
                        c -> c.getCode().split(" ")[0],
                        CodeInfo::getCode
                ));

        return badCodes.stream()
                .filter(codeMap::containsKey)
                .map(codeMap::get)
                .map(c -> c.replace(" ", "\u001d").replace("\"", "\\\""))
                .collect(Collectors.toList());
    }

    private void deleteCodesFromReport(String reportId, List<String> codes) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(codes);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId + "/codes"))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody)) // Исправленный вызов
                .build();

        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void acceptReport(String utilId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/code-usage-processor/api/code-usage-reports/accept-reports"))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[\"" + utilId + "\"]"))
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void recalculateOrder(String reportId) throws Exception {
        String orderNumber = getOrderNumber(reportId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(YamlUtil.host + "/api/manufacturing-order-process/" + orderNumber + "/recalculate"))
                .header("Authorization", "Bearer " + authService.getToken())
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private String getOrderNumber(String reportId) throws Exception {
        HttpResponse<String> response = sendGetRequest(
                YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports/" + reportId
        );
        return objectMapper.readTree(response.body()).get("orderNumber").asText();
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