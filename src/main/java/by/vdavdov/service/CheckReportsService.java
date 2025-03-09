package by.vdavdov.service;

import by.vdavdov.model.ContentItem;
import by.vdavdov.model.ReportsResponse;
import by.vdavdov.utils.YamlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckReportsService {
    private static final Logger log = LogManager.getLogger(CheckReportsService.class);
    private final AuthService authService = new AuthService();
    private final CancelService cancelService = new CancelService();
    private final LastRunService lastRunService = new LastRunService();

    public void getRejectedReports() throws Exception {
        Instant lastRun = lastRunService.getLastRunTime();
        Instant currentRun = Instant.now();

        int currentPage = 0;
        boolean hasMorePages = true;
        boolean hasErrors = false;

        while (hasMorePages && !hasErrors) {
            String url = YamlUtil.host + "/code-usage-processor/api/v2/code-usage-reports?"
                    + "query=status%3D%3DREJECTED%20and%20updated%3E%3D" + lastRun.toString()
                    + "&page=" + currentPage
                    + "&size=50";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = buildRequest(url);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Ошибка при получении страницы {}: {}", currentPage, response.statusCode());
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
            log.info("Все новые отчеты обработаны. Следующий запуск в {} минут", YamlUtil.scheduleInterval);
        }
    }

    private HttpRequest buildRequest(String url) throws Exception {
        return HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private void processPage(List<ContentItem> items) throws Exception {
        Map<String, String> reports = new HashMap<>();
        for (ContentItem item : items) {
            reports.put(item.getId(), item.getRejectionReason());
        }
        checkUtils(reports);
    }

    public void sentToCancel(String reportId) throws Exception {
        cancelService.cancel(reportId);
    }

    private void checkUtils(Map<String, String> rejectedReports) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        for (Map.Entry<String, String> entry : rejectedReports.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            String url = YamlUtil.host + "/api/utilization-reports/" + key;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + authService.getToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Не удалось получить информацию по утилю {} {} {}",
                        key, response.statusCode(), response.body());
            }
            if (response.statusCode() == 200) {
                if (!response.body().isEmpty()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(response.body());
                    if (jsonNode.isArray()) {
                        if (jsonNode.size() >= 2) {
                            log.info("По отчету {} 2 и более утиля, просьба обработать вручную", key);
                            continue;
                        }
                    }
                }
            }
            if (value.contains("отклонен, причина отклонения: Дубликат кода")) {
                sentToCancel(key);
            }
        }
    }

    private ReportsResponse parseResponse(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, ReportsResponse.class);
    }
}
