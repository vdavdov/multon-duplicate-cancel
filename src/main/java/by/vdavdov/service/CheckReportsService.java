package by.vdavdov.service;

import by.vdavdov.model.ContentItem;
import by.vdavdov.model.ReportsResponse;
import by.vdavdov.utils.YamlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckReportsService {
    private static final Logger log = LogManager.getLogger(CheckReportsService.class);
    private AtomicInteger page = new AtomicInteger(0);

    public Map<String, String> getRejectedReports() throws Exception {
        AuthService authService = new AuthService();
        String url = YamlUtil.host
                + "/code-usage-processor/api/v2/code-usage-reports?query=status%3D%3DREJECTED&page="
                + page.get()
                + "&size=50";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Ошибка при получении отчетов со статусом \"REJECTED\": {} \n {}", response.statusCode(), response.body());
            throw new RuntimeException(response.body());
        }

        if (response.statusCode() == 200) {
            log.info("Отчеты успешно получены, начинается проверка");
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                ReportsResponse reportsResponse = objectMapper.readValue(response.body(), ReportsResponse.class);
                Map<String, String> resultMap = new HashMap<>();
                for (ContentItem item : reportsResponse.getContent()) {
                    resultMap.put(item.getId(), item.getRejectionReason());
                }
                log.info("Список на проверку: {}", objectMapper.writeValueAsString(resultMap));
                return resultMap;
            } catch (IOException e) {
                e.printStackTrace();
            }
            page.incrementAndGet();
        }
        return null;
    }
}
