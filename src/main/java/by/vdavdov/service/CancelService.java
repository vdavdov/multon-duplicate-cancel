package by.vdavdov.service;

import by.vdavdov.utils.YamlUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CancelService {
    private static final Logger log = LogManager.getLogger(CancelService.class);

    public void cancel(String reportId) throws Exception {
        AuthService authService = new AuthService();
        log.info("Начало отмены отчетов");

        String jsonBody =
                """
                {
                "comment": "Дубликат кода"
                }
                """;

        String url = YamlUtil.host
                + "/code-usage-processor/api/v2/code-usage-reports/"
                + reportId
                + "/cancel";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + authService.getToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Не получилось отменить отчет с id - {}, ошибка {} {}",
                    reportId,
                    response.statusCode(),
                    response.body());
            throw new RuntimeException(response.body());
        }

        if (response.statusCode() == 200) {
            log.info("Отчет с id - {} успешно отменен",
                    reportId);
        }
    }

}
